package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.dto.response.QueueStatusResponse;
import com.example.event.exception.AppException;
import com.example.event.service.TicketQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketQueueServiceImpl implements TicketQueueService {
    private final RedisTemplate<String, String> redis;
    private final SecurityUtils securityUtils;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int MAX_BUYING = 2;
    private static final int BATCH_SIZE = 10;
    private static final long BUYING_TTL_MS = 600000;
    private static final long HB_TTL_MS = 30000;

    private static final String WAITING = "waiting:show:%s";
    private static final String BUYING = "buying:show:%s";
    private static final String HB = "hb:show:%s";
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
    public QueueStatusResponse joinQueue(String showId) {
        String userId = securityUtils.getCurrentUserId();
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
            long expiresIn = (long) ((buyingScore - System.currentTimeMillis()) / 1000);
            if (expiresIn <= 0) {
                log.warn("[QUEUE] Phiên mua vé của người dùng đã hết hạn | showId={}, userId={}, expiresIn={}", showId,
                        userId, expiresIn);
                throw new AppException(ErrorCode.BUYING_SESSION_EXPIRED);
            }
            log.info("[QUEUE] Người dùng tiếp tục phiên mua vé (BUYING) | showId={}, userId={}, expiresIn={}", showId,
                    userId, expiresIn);
            return QueueStatusResponse.builder()
                    .status("BUYING")
                    .expiresIn(expiresIn)
                    .build();
        }

        // Nếu nằm trong hàng đợi waiting
        if (position != null) {
            log.info("[QUEUE] Người dùng đã ở trong hàng đợi WAITING | showId={}, userId={}, position={}", showId,
                    userId, position + 1);
            return QueueStatusResponse.builder()
                    .position(position + 1)
                    .peopleAhead(position)
                    .status("WAITING")
                    .build();
        }

        // Nếu là người dùng mới
        long now = System.currentTimeMillis();
        long deadline = now + HB_TTL_MS;
        redis.executePipelined((RedisCallback<Object>) conn -> {
            byte[] uid = userId.getBytes();
            conn.zSetCommands().zAdd(
                    waitingKey.getBytes(),
                    System.currentTimeMillis(),
                    uid);
            conn.zSetCommands().zAdd(
                    hbKey.getBytes(),
                    deadline,
                    uid);
            return null;
        });

        position = redis.opsForZSet().rank(waitingKey, userId);
        if (position == null) {
            buyingScore = redis.opsForZSet().score(buyingKey, userId);
            if (buyingScore != null) {
                long expiresIn = (long) ((buyingScore - System.currentTimeMillis()) / 1000);
                log.info("[QUEUE] Người dùng được chuyển thẳng sang BUYING khi join queue | showId={}, userId={}",
                        showId, userId);
                return QueueStatusResponse.builder()
                        .status("BUYING")
                        .expiresIn(expiresIn)
                        .build();
            }
            log.error("[QUEUE] Không thể xác định vị trí trong hàng đợi | showId={}, userId={}", showId, userId);
            throw new AppException(ErrorCode.SYSTEM_ERROR,
                    "Không thể xác định vị trí trong hàng đợi, vui lòng thử lại.");
        }
        log.info("[QUEUE] Người dùng tham gia hàng đợi WAITING thành công | showId={}, userId={}, position={}", showId,
                userId, position + 1);
        return QueueStatusResponse.builder()
                .position(position + 1)
                .peopleAhead(position)
                .status("WAITING")
                .build();
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

        if (!inWaiting || !existsHeartBeat) {
            log.warn("[QUEUE] Không thể gia hạn heartbeat do người dùng không ở trong hàng đợi | showId={}, userId={}",
                    showId, userId);
            throw new AppException(ErrorCode.NOT_IN_QUEUE);
        }

        // Gia hạn heart beat
        long now = System.currentTimeMillis();
        long deadline = now + HB_TTL_MS;
        redis.opsForZSet().add(hbKey, userId, deadline);
    }

    @Override
    @Transactional
    public QueueStatusResponse getStatus(String showId) {
        String userId = securityUtils.getCurrentUserId();
        String waitingKey = String.format(WAITING, showId);
        String buyingKey = String.format(BUYING, showId);
        Double buyingScore = redis.opsForZSet().score(buyingKey, userId);
        if (buyingScore != null) {
            long expiresIn = (long) ((buyingScore - System.currentTimeMillis()) / 1000);
            if (expiresIn <= 0) {
                log.warn("[QUEUE] Trạng thái mua vé hết hạn | showId={}, userId={}, expiresIn={}", showId, userId,
                        expiresIn);
                throw new AppException(ErrorCode.BUYING_SESSION_EXPIRED);
            }
            return QueueStatusResponse.builder()
                    .status("BUYING")
                    .expiresIn(expiresIn)
                    .build();
        }
        Long position = redis.opsForZSet().rank(waitingKey, userId);
        if (position == null) {
            log.warn("[QUEUE] Kiểm tra trạng thái: người dùng không ở trong hàng đợi | showId={}, userId={}", showId,
                    userId);
            throw new AppException(ErrorCode.NOT_IN_QUEUE);
        }
        heartbeat(showId);
        return QueueStatusResponse.builder()
                .position(position + 1)
                .peopleAhead(position)
                .status("WAITING")
                .build();
    }

    @Override
    public void leaveQueue(String showId) {
        String userId = securityUtils.getCurrentUserId();
        if (userId != null) {
            leaveQueue(showId, userId);
        }
    }

    @Override
    public void leaveQueue(String showId, String userId) {
        if (userId == null || showId == null)
            return;
        log.info("[QUEUE] Người dùng rời khỏi hàng đợi | showId={}, userId={}", showId, userId);
        String waitingKey = String.format(WAITING, showId);
        String buyingKey = String.format(BUYING, showId);
        String hbKey = String.format(HB, showId);

        redis.executePipelined((RedisCallback<Object>) conn -> {
            byte[] rawKey = userId.getBytes();
            conn.zSetCommands().zRem(hbKey.getBytes(), rawKey);
            conn.zSetCommands().zRem(waitingKey.getBytes(), rawKey);
            conn.zSetCommands().zRem(buyingKey.getBytes(), rawKey);
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
            log.info("[QUEUE] Dọn dẹp {} người dùng mất kết nối (ghost users) | showId={}", expiredUsers.size(),
                    showId);
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
        if (available <= 0)
            return;

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
                String.valueOf(now));

        if (chosenUsers == null || chosenUsers.isEmpty())
            return;

        // Bắn WebSocket sau khi đã đảm bảo Redis đã nhận lệnh
        for (String userId : chosenUsers) {
            long expiresIn = (BUYING_TTL_MS + 10) / 1000;
            QueueStatusResponse wsMes = QueueStatusResponse.builder()
                    .status("BUYING")
                    .expiresIn(expiresIn)
                    .build();
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/p/show/" + showId + "/queue",
                    wsMes);
        }

        log.info("[QUEUE] Drain thành công {} người dùng chuyển sang trạng thái BUYING cho showId={}",
                chosenUsers.size(), showId);
    }

    @Override
    public boolean validateQueueAccess(String showId) {
        String userId = securityUtils.getCurrentUserId();
        String buyingKey = String.format(BUYING, showId);

        // Kiểm tra xem có còn trong danh sách Buying không
        Double expireAt = redis.opsForZSet().score(buyingKey, userId);
        if (expireAt == null || System.currentTimeMillis() > expireAt) {
            log.warn("[QUEUE] Phiên mua vé của người dùng đã quá hạn hoặc không tồn tại | showId={}, userId={}", showId,
                    userId);
            leaveQueue(showId);
            throw new AppException(ErrorCode.BUYING_SESSION_EXPIRED);
        }

        log.info("[QUEUE] Xác thực quyền mua vé thành công | showId={}, userId={}", showId, userId);
        return true;
    }

    @Override
    public long getRemainingTimeSeconds(String showId, String userId) {
        String buyingKey = String.format(BUYING, showId);

        // Lấy expireAt từ buying queue
        Double expireAt = redis.opsForZSet().score(buyingKey, userId);
        if (expireAt == null) {
            log.warn("[QUEUE] Không tìm thấy phiên mua vé khi lấy thời gian còn lại | showId={}, userId={}", showId,
                    userId);
            throw new AppException(ErrorCode.NOT_IN_QUEUE);
        }

        long now = System.currentTimeMillis();
        long remaining = expireAt.longValue() - now;

        // Nếu hết hạng đá user ra khỏi queue
        if (remaining <= 0) {
            log.warn("[QUEUE] Phiên mua vé đã hết hạn khi lấy thời gian còn lại | showId={}, userId={}", showId,
                    userId);
            leaveQueue(showId);
            throw new AppException(ErrorCode.BUYING_SESSION_EXPIRED);
        }

        return remaining / 1000;
    }
}
