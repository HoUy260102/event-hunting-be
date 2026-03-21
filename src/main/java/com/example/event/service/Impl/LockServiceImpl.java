package com.example.event.service.Impl;

import com.example.event.constant.ErrorCode;
import com.example.event.dto.request.ReservationItemReq;
import com.example.event.entity.ReservationItem;
import com.example.event.exception.AppException;
import com.example.event.service.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockServiceImpl implements LockService {
    private final RedisTemplate<String, String> redisTemplate;
    private static final int LOCK_TIMEOUT_SECONDS = 300;

    private static final String BULK_RESERVE_UNASSIGN_LUA = "local itemCount = #ARGV / 3 " +
            "if #ARGV % 3 ~= 0 then return 'ERR_INVALID_ARGV' end " +
            "if #KEYS ~= itemCount * 4 then return 'ERR_INVALID_KEYS' end " +

            "for i = 1, itemCount do " +
            "    local argIdx = (i - 1) * 3 " +
            "    local keyIdx = (i - 1) * 4 " +

            "    local buyQty = tonumber(ARGV[argIdx + 1]) " +
            "    local typeId = ARGV[argIdx + 2] " +
            "    local tierId = ARGV[argIdx + 3] " +

            "    if not buyQty or buyQty <= 0 then return 'ERR_INVALID_QTY' end " +

            "    local typeTotal = tonumber(redis.call('GET', KEYS[keyIdx + 1]) or '0') " +
            "    local typeRes   = tonumber(redis.call('GET', KEYS[keyIdx + 2]) or '0') " +
            "    local tierLimit = tonumber(redis.call('GET', KEYS[keyIdx + 3]) or '0') " +
            "    local tierRes   = tonumber(redis.call('GET', KEYS[keyIdx + 4]) or '0') " +

            "    if (typeRes + buyQty) > typeTotal then return 'ERR_TYPE_FULL:' .. typeId end " +
            "    if (tierRes + buyQty) > tierLimit then return 'ERR_TIER_FULL:' .. tierId end " +
            "end " +

            "for i = 1, itemCount do " +
            "    local argIdx = (i - 1) * 3 " +
            "    local keyIdx = (i - 1) * 4 " +
            "    local buyQty = tonumber(ARGV[argIdx + 1]) " +
            "    redis.call('INCRBY', KEYS[keyIdx + 2], buyQty) " +
            "    redis.call('INCRBY', KEYS[keyIdx + 4], buyQty) " +
            "end " +

            "return 'OK'";

    private static final String RELEASE_UNASSIGN_LUA = "local itemCount = #ARGV " +
            "for i = 1, itemCount do " +
            "    local qty = tonumber(ARGV[i]) " +
            "    local kIdx = (i - 1) * 2 " +
            "    local typeKey = KEYS[kIdx + 1] " +
            "    local tierKey = KEYS[kIdx + 2] " +
            "    " +
            "    local function safe_decr(key, amount) " +
            "        local current = tonumber(redis.call('GET', key) or '0') " +
            "        local newValue = math.max(0, current - amount) " +
            "        redis.call('SET', key, tostring(newValue)) " +
            "    end " +
            "    " +
            "    if typeKey then safe_decr(typeKey, qty) end " +
            "    if tierKey then safe_decr(tierKey, qty) end " +
            "end " +
            "return 1";

    private static final String SEAT_LOCK_LUA =
            "local seatCount = #KEYS " +
                    "for i = 1, seatCount do " +
                    "    if redis.call('EXISTS', KEYS[i]) == 1 then return 'ERR_SEAT_OCCUPIED:' .. ARGV[i + 2] end " +
                    "end " +
                    "for i = 1, seatCount do " +
                    "    redis.call('SET', KEYS[i], ARGV[1]) " +
                    "    redis.call('EXPIRE', KEYS[i], tonumber(ARGV[2])) " +
                    "end " +
                    "return 'OK'";

    private static final String SEAT_UNLOCK_LUA =
            "local seatCount = #KEYS " +
                    "for i = 1, seatCount do " +
                    "    redis.call('DEL', KEYS[i]) " +
                    "end " +
                    "return 'OK'";

    private static final String KEY_TYPE_TOTAL = "ticket_type:{show:%s}:%s:total";
    private static final String KEY_TYPE_RESERVED = "ticket_type:{show:%s}:%s:reserved";
    private static final String KEY_TIER_LIMIT = "ticket_tier:{show:%s}:%s:limit";
    private static final String KEY_TIER_RESERVED = "ticket_tier:{show:%s}:%s:reserved";
    private static final String KEY_SEAT_STATUS = "ticket_seat:{show:%s}:%s:lock";

    private final DefaultRedisScript<String> bulkUnassginReserveScript = createScript(BULK_RESERVE_UNASSIGN_LUA, String.class);
    private final DefaultRedisScript<Long> releaseUnassignScript = createScript(RELEASE_UNASSIGN_LUA, Long.class);
    private final DefaultRedisScript<String> seatLockScript = createScript(SEAT_LOCK_LUA, String.class);
    private final DefaultRedisScript<String> seatUnlockScript = createScript(SEAT_UNLOCK_LUA, String.class);

    private <T> DefaultRedisScript<T> createScript(String text, Class<T> type) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(text);
        script.setResultType(type);
        return script;
    }

    public void initEventData(String showId, String typeId, Long total, String tierId, Long limit) {
        redisTemplate.opsForValue().set(String.format(KEY_TYPE_TOTAL, showId, typeId), total.toString());
        redisTemplate.opsForValue().set(String.format(KEY_TIER_LIMIT, showId, tierId), limit.toString());
        redisTemplate.opsForValue().setIfAbsent(String.format(KEY_TYPE_RESERVED, showId, typeId), "0");
        redisTemplate.opsForValue().setIfAbsent(String.format(KEY_TIER_RESERVED, showId, tierId), "0");
    }

    @Override
    public void reserveUnassignedTickets(String showId, List<ReservationItemReq> requests) {
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        for (ReservationItemReq req : requests) {
            keys.add(String.format(KEY_TYPE_TOTAL, showId, req.getTicketTypeId()));
            keys.add(String.format(KEY_TYPE_RESERVED, showId, req.getTicketTypeId()));
            keys.add(String.format(KEY_TIER_LIMIT, showId, req.getTicketTierId()));
            keys.add(String.format(KEY_TIER_RESERVED, showId, req.getTicketTierId()));

            args.add(req.getQuantity().toString());
            args.add(req.getTicketTypeId());
            args.add(req.getTicketTierId());
        }
        String result;
        try {
            result = redisTemplate.execute(bulkUnassginReserveScript, keys, args.toArray());
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw e;
            log.error("Redis execution error", e);
            throw new RuntimeException("Hệ thống bận, vui lòng thử lại sau.");
        }
        if (result == null || !"OK".equals(result)) {
            handleReservationError(result, requests);
        }
        log.info("Successfully reserved tickets for show: {}", showId);
    }

    @Override
    public void lockSeats(String showId, List<ReservationItemReq> req, String userId) {
        if (req == null || req.isEmpty()) return;
        List<String> sortedSeatIds = req.stream()
                .flatMap(item -> item.getSeatIds().stream())
                .filter(id -> id != null && !id.trim().isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (sortedSeatIds.isEmpty()) return;

        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        args.add(userId);
        args.add(Integer.toString(LOCK_TIMEOUT_SECONDS));
        sortedSeatIds.forEach(seatId -> {
            keys.add(String.format(KEY_SEAT_STATUS, showId, seatId));
            args.add(seatId);
        });
        try {
            String result = redisTemplate.execute(seatLockScript, keys, args.toArray());
            if (result != null && result.startsWith("ERR_SEAT_OCCUPIED:")) {
                String occupiedSeatId = result.substring(18);
                log.warn("[SeatLock] Show {}: Ghế {} đã bị chiếm bởi người khác", showId, occupiedSeatId);
                throw new AppException(ErrorCode.SEAT_ALREADY_RESERVED,
                        "Ghế " + occupiedSeatId + " đã có người chọn rồi bạn ơi!");
            }
            if ("OK".equals(result)) {
                String seatIdsForLog = sortedSeatIds.stream()
                        .collect(Collectors.joining(","));
                log.info("[SeatLock] User {} đã khóa thành công các ghế: [{}] cho show {}", userId, seatIdsForLog, showId);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khóa ghế Redis", e);
            throw new RuntimeException("Hệ thống bận, không thể chọn ghế lúc này.");
        }
    }

    @Override
    public void unlockSeats(String showId, List<ReservationItemReq> req) {
        if (req == null || req.isEmpty()) return;
        List<String> seatIds = req.stream()
                .flatMap(item -> item.getSeatIds().stream())
                .filter(id -> id != null && !id.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (seatIds.isEmpty()) return;

        List<String> keys = new ArrayList<>();
        for (String seatId : seatIds) {
            keys.add(String.format(KEY_SEAT_STATUS, showId, seatId));
        }
        try {
            String result = redisTemplate.execute(seatUnlockScript, keys);
            if ("OK".equals(result)) {
                log.info("[SeatUnlock] Đã giải phóng thành công {} ghế cho show: {}", seatIds.size(), showId);
            }
        } catch (Exception e) {
            log.error("Lỗi khi giải phóng ghế Redis cho show: {}", showId, e);
        }
    }

    @Override
    public void releaseUnassignedTickets(String showId, List<ReservationItemReq> requests) {
        if (requests == null || requests.isEmpty()) return;

        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        for (ReservationItemReq req : requests) {
            keys.add(String.format(KEY_TYPE_RESERVED, showId, req.getTicketTypeId()));
            keys.add(String.format(KEY_TIER_RESERVED, showId, req.getTicketTierId()));

            args.add(req.getQuantity().toString());
        }

        try {
            redisTemplate.execute(releaseUnassignScript, keys, args.toArray());
            log.info("Giải phóng thành công {} items của show: {}", requests.size(), showId);
        } catch (Exception e) {
            log.error("Critical error in batch release for show: {}", showId, e);
            throw new RuntimeException("Không thể giải phóng vé, vui lòng kiểm tra lại hệ thống.");
        }
    }

    @Override
    public void releaseUnassignedReservationItem(String showId, List<ReservationItem> requests) {
        if (requests == null || requests.isEmpty()) return;

        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        for (ReservationItem req : requests) {
            if (req.getSeat() != null) continue;
            keys.add(String.format(KEY_TYPE_RESERVED, showId, req.getTicketType().getId()));
            keys.add(String.format(KEY_TIER_RESERVED, showId, req.getTicketTier().getId()));
            args.add(req.getQuantity().toString());
        }

        try {
            if (keys.isEmpty()) {
                log.warn("Không có keys nào được tạo để giải phóng cho show: {}", showId);
                return;
            }
            redisTemplate.execute(releaseUnassignScript, keys, args.toArray());
            log.info("Giải phóng thành công {} items của show: {}", requests.size(), showId);
        } catch (Exception e) {
            log.error("Critical error in batch release for show: {}", showId, e);
            throw new RuntimeException("Không thể giải phóng vé, vui lòng kiểm tra lại hệ thống.");
        }
    }

    @Override
    public void unlockSeatsReservationItem(String showId, List<ReservationItem> req) {
        if (req == null || req.isEmpty()) return;
        List<String> seatIds = req.stream()
                .filter(item -> item.getSeat() != null)
                .map(item -> item.getSeat().getId())
                .collect(Collectors.toList());
        if (seatIds.isEmpty()) return;

        List<String> keys = new ArrayList<>();
        for (String seatId : seatIds) {
            keys.add(String.format(KEY_SEAT_STATUS, showId, seatId));
        }

        try {
            String result = redisTemplate.execute(seatUnlockScript, keys);
            if ("OK".equals(result)) {
                log.info("[SeatUnlock] Đã giải phóng thành công {} ghế cho show: {}", seatIds.size(), showId);
            }
        } catch (Exception e) {
            log.error("Lỗi khi giải phóng ghế Redis cho show: {}", showId, e);
        }
    }

    private void handleReservationError(String result, List<ReservationItemReq> requests) {
        if (result == null) {
            throw new RuntimeException("Phản hồi từ hệ thống không hợp lệ.");
        }

        String failedId;

        if (result.startsWith("ERR_TYPE_FULL:")) {
            failedId = result.substring("ERR_TYPE_FULL:".length());

            String typeName = requests.stream()
                    .filter(r -> r.getTicketTypeId().equals(failedId))
                    .findFirst()
                    .map(ReservationItemReq::getTicketTypeName)
                    .orElse("loại vé này");

            log.warn("Ticket Type full: {}", failedId);
            throw new AppException(ErrorCode.TICKET_TYPE_FULL,
                    "Loại vé " + typeName + " đã hết hàng!");
        }

        if (result.startsWith("ERR_TIER_FULL:")) {
            failedId = result.substring("ERR_TIER_FULL:".length());

            String tierName = requests.stream()
                    .filter(r -> r.getTicketTierId().equals(failedId))
                    .findFirst()
                    .map(ReservationItemReq::getTicketTierName)
                    .orElse("hạng vé này");

            log.warn("Ticket Tier full: {}", failedId);
            throw new AppException(ErrorCode.TICKET_TIER_FULL,
                    "Hạng vé " + tierName + " đã đạt giới hạn đặt chỗ!");
        }

        if ("ERR_INVALID_QTY".equals(result)) {
            throw new RuntimeException("Số lượng vé không hợp lệ.");
        }

        if ("ERR_INVALID_ARGV".equals(result) || "ERR_INVALID_KEYS".equals(result)) {
            throw new RuntimeException("Dữ liệu đặt vé không hợp lệ.");
        }

        throw new RuntimeException("Đặt vé thất bại, vui lòng thử lại.");
    }
}