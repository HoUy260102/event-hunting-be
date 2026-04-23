package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.exception.AppException;
import com.example.event.service.TicketQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TicketQueueServiceImpl implements TicketQueueService {
    private final RedisTemplate<String, String> redis;
    private final SecurityUtils securityUtils;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int MAX_BUYING = 2;
    private static final int BATCH_SIZE = 10;
    //    private static final long BUYING_TTL_MS = 600000;
    private static final long BUYING_TTL_MS = 300000;
    private static final long HB_TTL_MS = 30000;

    private static final String WAITING = "waiting:show:%s";
    private static final String BUYING = "buying:show:%s";
    private static final String HB = "hb:show:%s";
    private static final String TOKEN = "ticket:token:show:%s:user:%s";
    private static final String ACTIVE_SHOWS_KEY = "active_shows";

    private static final String DRAIN_SCRIPT = """
            local waitingKey  = KEYS[1]
            local buyingKey   = KEYS[2]
            local hbKey       = KEYS[3]
            local available   = tonumber(ARGV[1])
            local maxCheck    = tonumber(ARGV[2])
            local buyExpireAt = tonumber(ARGV[3])
            local now         = tonumber(ARGV[4])
            
            local result  = {}
            local checked = 0
            
            while #result < available and checked < maxCheck do
                local res = redis.call('ZPOPMIN', waitingKey, 1)
                if #res == 0 then break end
            
                local userId  = res[1]
                checked = checked + 1
            
                local deadline = redis.call('ZSCORE', hbKey, userId)
                if deadline and tonumber(deadline) >= now then
                    redis.call('ZADD', buyingKey, buyExpireAt, userId)
                    redis.call('ZREM', hbKey, userId)
                    table.insert(result, userId)
                end
            end
            
            return result
            """;

    @Override
    public Map<String, Object> joinQueue(String showId) {
        String userId = securityUtils.getCurrentUserId();
        Map<String, Object> result = new HashMap<>();
        String waitingKey = String.format(WAITING, showId);
        String buyingKey = String.format(BUYING, showId);
        String hbKey = String.format(HB, showId);

        // Đưa show vào list active show
        redis.opsForZSet().addIfAbsent(ACTIVE_SHOWS_KEY, showId, System.currentTimeMillis());

        // Kiểm tra xem đã trong hàng ợi chưa
        Long position = redis.opsForZSet().rank(waitingKey, userId);

        // Nếu nằm trong hàng đợi buying
        Double buyingScore = redis.opsForZSet().score(buyingKey, userId);
        if (buyingScore != null) {
            String tokenKey = String.format(TOKEN, showId, userId);
            String token = redis.opsForValue().get(tokenKey);
            if (token == null) {
                throw new AppException(ErrorCode.BUYING_SESSION_EXPIRED);
            }
            long expiresIn = (long) ((buyingScore - System.currentTimeMillis()) / 1000);
            if (expiresIn <= 0) throw new AppException(ErrorCode.BUYING_SESSION_EXPIRED);
            result.put("status", "BUYING");
            result.put("token", token);
            result.put("expiresIn", expiresIn);
            return result;
        }

        // Nếu nằm trong hàng đợi waiting
        if (position != null) {
            result.put("position", position + 1);
            result.put("peopleAhead", position);
            result.put("status", "WAITING");
            return result;
        }

        // Nếu là người dùng mới
        long now = System.currentTimeMillis();
        long deadline = now + HB_TTL_MS;
        redis.executePipelined((RedisCallback<Object>) conn -> {
            byte[] uid = userId.getBytes();
            conn.zSetCommands().zAdd(
                    waitingKey.getBytes(),
                    System.currentTimeMillis(),
                    uid
            );
            conn.zSetCommands().zAdd(
                    hbKey.getBytes(),
                    deadline,
                    uid
            );
            return null;
        });

        position = redis.opsForZSet().rank(waitingKey, userId);
        result.put("position", position + 1);
        result.put("peopleAhead", position);
        result.put("status", "WAITING");
        return result;
    }

    private void heartbeat(String showId) {
        String userId = securityUtils.getCurrentUserId();
        String waitingKey = String.format(WAITING, showId);
        String buyingKey = String.format(BUYING, showId);
        String hbKey = String.format(HB, showId);

        // Kiểm tra người dùng có nằm trong hàng buying
        Double buyingScore = redis.opsForZSet().score(buyingKey, userId);
        if (buyingScore != null) {
            return;
        }

        // Kiểm tra người dùng có nằm trong hàng buying và kiểm tra còn heartbeat
        boolean inWaiting = redis.opsForZSet().score(waitingKey, userId) != null;
        boolean existsHeartBeat = redis.opsForZSet().score(hbKey, userId) != null;
        ;
        if (!inWaiting || !existsHeartBeat) {
            throw new AppException(ErrorCode.NOT_IN_QUEUE);
        }

        // Gia hạn heart beat
        long now = System.currentTimeMillis();
        long deadline = now + HB_TTL_MS;
        redis.opsForZSet().add(hbKey, userId, deadline);
    }

    @Override
    @Transactional
    public Map<String, Object> getStatus(String showId) {
        String userId = securityUtils.getCurrentUserId();
        Map<String, Object> result = new HashMap<>();
        String waitingKey = String.format(WAITING, showId);
        String buyingKey = String.format(BUYING, showId);
        Double buyingScore = redis.opsForZSet().score(buyingKey, userId);
        if (buyingScore != null) {
            String tokenKey = String.format(TOKEN, showId, userId);
            String token = redis.opsForValue().get(tokenKey);
            if (token == null) {
                throw new AppException(ErrorCode.BUYING_SESSION_EXPIRED);
            }
            long expiresIn = (long) ((buyingScore - System.currentTimeMillis()) / 1000);
            if (expiresIn <= 0) throw new AppException(ErrorCode.BUYING_SESSION_EXPIRED);
            result.put("status", "BUYING");
            result.put("token", token);
            result.put("expiresIn", expiresIn);
            return result;
        }
        Long position = redis.opsForZSet().rank(waitingKey, userId);
        if (position == null) {
            throw new AppException(ErrorCode.NOT_IN_QUEUE);
        }
        heartbeat(showId);
        result.put("position", position + 1);
        result.put("peopleAhead", position);
        result.put("status", "WAITING");
        return result;
    }

    @Override
    public void leaveQueue(String showId) {
        String userId = securityUtils.getCurrentUserId();
        String waitingKey = String.format(WAITING, showId);
        String buyingKey = String.format(BUYING, showId);
        String hbKey = String.format(HB, showId);

        String tokenKey = String.format(TOKEN, showId, userId);
        redis.executePipelined((RedisCallback<Object>) conn -> {
            byte[] rawKey = userId.getBytes();
            conn.zSetCommands().zRem(hbKey.getBytes(), rawKey);
            conn.zSetCommands().zRem(waitingKey.getBytes(), rawKey);
            conn.zSetCommands().zRem(buyingKey.getBytes(), rawKey);
            conn.keyCommands().del(tokenKey.getBytes());
            return null;
        });
    }

    @Override
    public void cleanupGhostUsers(String showId) {
        String waitingKey = String.format(WAITING, showId);
        String hbKey = String.format(HB, showId);
        long now = System.currentTimeMillis();

        // Lấy ĐÚNG 500 đứa hết hạn
        Set<String> expiredUsers = redis.opsForZSet().rangeByScore(hbKey, 0, now, 0, 500);

        if (expiredUsers != null && !expiredUsers.isEmpty()) {
            redis.executePipelined((RedisCallback<Object>) conn -> {
                byte[][] rawKeys = expiredUsers.stream().map(String::getBytes).toArray(byte[][]::new);
                conn.zSetCommands().zRem(hbKey.getBytes(), rawKeys);
                conn.zSetCommands().zRem(waitingKey.getBytes(), rawKeys);
                return null;
            });
        }
    }

    @Override
    public void drainQueue(String showId) {
        String waitingKey = String.format(WAITING, showId);
        String buyingKey = String.format(BUYING, showId);
        String hbKey = String.format(HB, showId);

        // Dọn user hết hạn mua
        redis.opsForZSet().removeRangeByScore(buyingKey, 0, System.currentTimeMillis());

        // Tính slot trống
        Long buying = redis.opsForZSet().zCard(buyingKey);
        long available = MAX_BUYING - (buying != null ? buying : 0);
        if (available <= 0) return;

        // Lua script - pop user thật, skip ghost, atomic
        long expireAt = System.currentTimeMillis() + BUYING_TTL_MS;
        long now = System.currentTimeMillis();
        RedisScript<List> script = RedisScript.of(DRAIN_SCRIPT, List.class);

        List<String> chosenUsers = redis.execute(
                script,
                new ArrayList<>(Arrays.asList(waitingKey, buyingKey, hbKey)),
                String.valueOf(available),
                String.valueOf(Math.min(BATCH_SIZE, available) * 3),
                String.valueOf(expireAt),
                String.valueOf(now)
        );

        if (chosenUsers == null || chosenUsers.isEmpty()) return;

        Map<String, String> userTokenMap = new HashMap<>();
        for (String userId : chosenUsers) {
            userTokenMap.put(userId, UUID.randomUUID().toString());
        }

        // Thực thi ghi vào Redis hàng loạt (Pipeline)
        redis.executePipelined((RedisCallback<Object>) conn -> {
            userTokenMap.forEach((userId, token) -> {
                String tokenKey = String.format(TOKEN, showId, userId);
                conn.stringCommands().setEx(
                        tokenKey.getBytes(),
                        (BUYING_TTL_MS + 10) / 1000,
                        token.getBytes()
                );
            });
            return null;
        });

        // Bắn WebSocket sau khi đã đảm bảo Redis đã nhận lệnh
        userTokenMap.forEach((userId, token) -> {
            Map<String, Object> mes = new HashMap<>();
            mes.put("status", "BUYING");
            mes.put("token", token);
            long expiresIn = (BUYING_TTL_MS + 10) / 1000;
            mes.put("expiresIn", expiresIn);
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/p/show/" + showId + "/queue",
                    mes
            );
        });
    }

    @Override
    public boolean validateQueueToken(String showId, String clientToken) {
        String userId = securityUtils.getCurrentUserId();
        String tokenKey = String.format(TOKEN, showId, userId);
        String buyingKey = String.format(BUYING, showId);

        // Lấy token từ Redis
        String savedToken = redis.opsForValue().get(tokenKey);
        if (savedToken == null || !savedToken.equals(clientToken)) {
            throw new AppException(ErrorCode.INVALID_QUEUE_TOKEN);
        }

        // Kiểm tra xem có còn trong danh sách Buying không
        Double expireAt = redis.opsForZSet().score(buyingKey, userId);
        if (expireAt == null || System.currentTimeMillis() > expireAt) {
            leaveQueue(showId);
            throw new AppException(ErrorCode.BUYING_SESSION_EXPIRED);
        }

        return true;
    }

    @Override
    public long getRemainingTimeSeconds(String showId, String userId) {
        String buyingKey = String.format(BUYING, showId);

        // Lấy expireAt từ buying queue
        Double expireAt = redis.opsForZSet().score(buyingKey, userId);
        if (expireAt == null) {
            throw new AppException(ErrorCode.NOT_IN_QUEUE);
        }

        long now = System.currentTimeMillis();
        long remaining = expireAt.longValue() - now;

        // Nếu hết hạng đá user ra khỏi queue
        if (remaining <= 0) {
            leaveQueue(showId);
            throw new AppException(ErrorCode.BUYING_SESSION_EXPIRED);
        }

        return remaining / 1000;
    }
}
