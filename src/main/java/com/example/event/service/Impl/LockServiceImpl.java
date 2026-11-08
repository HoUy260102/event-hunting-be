package com.example.event.service.Impl;

import com.example.event.constant.ErrorCode;
import com.example.event.dto.request.ReservationItemReq;
import com.example.event.entity.ReservationItem;
import com.example.event.exception.AppException;
import com.example.event.repository.SeatRepository;
import com.example.event.service.LockService;
import com.example.event.service.TicketQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockServiceImpl implements LockService {
    private final RedisTemplate<String, String> redisTemplate;
    private final SeatRepository seatRepository;
    private final TicketQueueService ticketQueueService;

    private static final int EXTRA_PERIOD_SECONDS = 40;
    private static final String SEAT_LOCK_LUA = "local seatCount = #KEYS " +
            "local occupiedSeats = {} " +
            "for i = 1, seatCount do " +
            "    if redis.call('EXISTS', KEYS[i]) == 1 then " +
            "        table.insert(occupiedSeats, ARGV[i + 2]) " +
            "    end " +
            "end " +
            "if #occupiedSeats > 0 then " +
            "    return 'ERR_SEATS_OCCUPIED:' .. table.concat(occupiedSeats, ',') " +
            "end " +
            "for i = 1, seatCount do " +
            "    redis.call('SET', KEYS[i], ARGV[1]) " +
            "    redis.call('EXPIRE', KEYS[i], tonumber(ARGV[2])) " +
            "end " +
            "return 'OK'";

    private static final String SEAT_UNLOCK_LUA = "local lockToken = ARGV[1] " +
            "local seatCount = #KEYS " +
            "for i = 1, seatCount do " +
            "    if redis.call('GET', KEYS[i]) == lockToken then " +
            "        redis.call('DEL', KEYS[i]) " +
            "    end " +
            "end " +
            "return 'OK'";

    private static final String KEY_SEAT_STATUS = "ticket_seat:{show:%s}:%s:lock";
    private final DefaultRedisScript<String> seatLockScript = createScript(SEAT_LOCK_LUA, String.class);
    private final DefaultRedisScript<String> seatUnlockScript = createScript(SEAT_UNLOCK_LUA, String.class);

    private <T> DefaultRedisScript<T> createScript(String text, Class<T> type) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptText(text);
        script.setResultType(type);
        return script;
    }

    @Override
    public void lockSeats(String showId, List<ReservationItemReq> req, String userId, String lockToken) {
        if (req == null || req.isEmpty())
            return;
        List<String> sortedSeatIds = req.stream()
                .flatMap(item -> item.getSeatIds().stream())
                .filter(id -> id != null && !id.trim().isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (sortedSeatIds.isEmpty())
            return;
        long lockTimeOutSeconds = ticketQueueService.getRemainingTimeSeconds(showId, userId);
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        args.add(lockToken);
        args.add(Integer.toString((int) lockTimeOutSeconds + EXTRA_PERIOD_SECONDS));
        sortedSeatIds.forEach(seatId -> {
            keys.add(String.format(KEY_SEAT_STATUS, showId, seatId));
            args.add(seatId);
        });
        try {
            String result = redisTemplate.execute(seatLockScript, keys, args.toArray());
            if (result != null && result.startsWith("ERR_SEATS_OCCUPIED:")) {
                List<String> occupiedSeatIds = Arrays.asList(result.substring(19).split(","));
                String occupiedSeats = seatRepository.findSeatsByIdIn(occupiedSeatIds)
                        .stream()
                        .map(seat -> seat.getRowName() + "-" + seat.getSeatNumber())
                        .collect(Collectors.joining(", "));
                log.warn("[LOCK] Show {}: Ghế {} đã bị chiếm bởi người khác", showId, occupiedSeats);
                throw new AppException(ErrorCode.SEAT_ALREADY_RESERVED,
                        "Ghế: " + occupiedSeats + " đã có người chọn rồi bạn ơi!");
            }
            if ("OK".equals(result)) {
                String seatIdsForLog = sortedSeatIds.stream()
                        .collect(Collectors.joining(","));
                log.info("[LOCK] User {} đã khóa thành công các ghế: [{}] cho show {}", userId, seatIdsForLog,
                        showId);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("[LOCK] Lỗi khóa ghế Redis cho show {}: ", showId, e);
            throw new RuntimeException("Hệ thống bận, không thể chọn ghế lúc này.");
        }
    }

    @Override
    public void unlockSeats(String showId, List<ReservationItemReq> req, String lockToken) {
        if (req == null || req.isEmpty() || lockToken == null || lockToken.isBlank())
            return;
        List<String> seatIds = req.stream()
                .flatMap(item -> item.getSeatIds().stream())
                .filter(id -> id != null && !id.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (seatIds.isEmpty())
            return;

        List<String> keys = new ArrayList<>();
        for (String seatId : seatIds) {
            keys.add(String.format(KEY_SEAT_STATUS, showId, seatId));
        }
        try {
            String result = redisTemplate.execute(seatUnlockScript, keys, lockToken);
            if ("OK".equals(result)) {
                log.info("[LOCK] Đã giải phóng thành công {} ghế cho show: {}", seatIds.size(), showId);
            }
        } catch (Exception e) {
            log.error("[LOCK] Lỗi khi giải phóng ghế Redis cho show: {}", showId, e);
        }
    }

    @Override
    public void unlockSeatsReservationItem(String showId, List<ReservationItem> req, String lockToken) {
        if (req == null || req.isEmpty() || lockToken == null || lockToken.isBlank())
            return;
        List<String> seatIds = req.stream()
                .filter(item -> item.getSeat() != null)
                .map(item -> item.getSeat().getId())
                .collect(Collectors.toList());
        if (seatIds.isEmpty())
            return;

        List<String> keys = new ArrayList<>();
        for (String seatId : seatIds) {
            keys.add(String.format(KEY_SEAT_STATUS, showId, seatId));
        }

        try {
            String result = redisTemplate.execute(seatUnlockScript, keys, lockToken);
            if ("OK".equals(result)) {
                log.info("[LOCK] Đã giải phóng thành công {} ghế cho show: {}", seatIds.size(), showId);
            }
        } catch (Exception e) {
            log.error("[LOCK] Lỗi khi giải phóng ghế Redis cho show: {}", showId, e);
        }
    }
}
