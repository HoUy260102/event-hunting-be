package com.example.event.job;

import com.example.event.constant.ShowStatus;
import com.example.event.entity.Show;
import com.example.event.entity.TicketTier;
import com.example.event.entity.TicketType;
import com.example.event.repository.ShowRepository;
import com.example.event.repository.TicketTierRepository;
import com.example.event.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketReconciliationJob {

    private final ShowRepository showRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTierRepository ticketTierRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_TYPE_TOTAL = "ticket_type:{show:%s}:%s:total";
    private static final String KEY_TYPE_RESERVED = "ticket_type:{show:%s}:%s:reserved";
    private static final String KEY_TIER_LIMIT = "ticket_tier:{show:%s}:%s:limit";
    private static final String KEY_TIER_RESERVED = "ticket_tier:{show:%s}:%s:reserved";
    private static final String KEY_SHOW_SYNC_LOCK = "sync_lock:{show:%s}";

    private static final int DRAIN_SLEEP_MS = 1500; // Chờ 1.5s xả cạn request in-flight
    private static final int SYNC_LOCK_TTL_SECONDS = 10; // Khóa tối đa 10s tự động hết hạn

    /**
     * Chạy định kỳ vào 02:00 sáng mỗi ngày.
     * Áp dụng Double-Checked Locking:
     * 1. Fast Check không khóa.
     * 2. Nếu lệch -> Bật sync_lock và sleep 1.5s để xả cạn in-flight.
     * 3. Verify Check lần 2 -> Nếu vẫn lệch mới đồng bộ Redis từ DB.
     */
    @Scheduled(cron = "${app.reconciliation.cron:0 0 2 * * ?}")
    @SchedulerLock(name = "ticketReconciliationJob", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void reconcileTicketStockDaily() {
        log.info("[RECONCILIATION] Bắt đầu tiến trình kiểm toán & đồng bộ tồn kho vé giữa DB và Redis...");

        // 1. Lấy danh sách các suất diễn đang hoạt động / mở bán (chưa kết thúc)
        LocalDateTime now = LocalDateTime.now();
        List<Show> activeShows = showRepository.findActiveShowsForReconciliation(
                List.of(ShowStatus.ACTIVE, ShowStatus.ON_SALE, ShowStatus.UPCOMING, ShowStatus.HAPPENING),
                now
        );

        if (activeShows == null || activeShows.isEmpty()) {
            log.info("[RECONCILIATION] Không có suất diễn nào đang hoạt động cần kiểm toán tồn kho.");
            return;
        }

        for (Show show : activeShows) {
            try {
                reconcileSingleShow(show);
            } catch (Exception e) {
                log.error("[RECONCILIATION] Lỗi khi đối soát show {}: {}", show.getId(), e.getMessage(), e);
            }
        }

        log.info("[RECONCILIATION] Hoàn tất tiến trình kiểm toán & đồng bộ tồn kho vé.");
    }

    private void reconcileSingleShow(Show show) {
        String showId = show.getId();

        // --- BƯỚC 1: FAST CHECK (Không khóa) ---
        boolean hasMismatch = checkShowMismatch(show);
        if (!hasMismatch) {
            // 99% trường hợp: Số liệu khớp hoàn hảo -> Bỏ qua ngay
            return;
        }

        log.warn("[RECONCILIATION] Phát hiện nghi vấn lệch tồn kho tại Show {}. Tiến hành quy trình khóa vi mô & xác thực...", showId);

        // --- BƯỚC 2: KHÓA SHOW VI MÔ + DRAIN PIPELINE ---
        String syncLockKey = String.format(KEY_SHOW_SYNC_LOCK, showId);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(syncLockKey, "LOCKED", SYNC_LOCK_TTL_SECONDS, TimeUnit.SECONDS);

        if (!Boolean.TRUE.equals(acquired)) {
            log.warn("[RECONCILIATION] Show {} đang bị khóa bởi tiến trình khác, bỏ qua lượt này.", showId);
            return;
        }

        try {
            // Chờ 1.5s để tất cả request in-flight đã lọt qua trước đó commit xong vào DB
            Thread.sleep(DRAIN_SLEEP_MS);

            // Tải lại dữ liệu mới nhất của Show từ DB sau khi các request in-flight đã commit
            Show freshShow = showRepository.findShowById(showId);
            if (freshShow == null || freshShow.getDeletedAt() != null) {
                return;
            }

            // --- BƯỚC 3: VERIFY CHECK (Kiểm tra lại lần 2) & ĐỒNG BỘ NẾU VẪN LỆCH ---
            syncShowIfStillMismatched(freshShow);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[RECONCILIATION] Luồng sleep bị ngắt quãng khi đối soát show {}", showId, e);
        } finally {
            // --- BƯỚC 4: MỞ KHÓA SHOW ---
            redisTemplate.delete(syncLockKey);
            log.info("[RECONCILIATION] Đã giải phóng sync_lock cho Show {}", showId);
        }
    }

    private boolean checkShowMismatch(Show show) {
        String showId = show.getId();
        if (show.getTicketTypes() == null) return false;

        for (TicketType type : show.getTicketTypes()) {
            if (type.getDeletedAt() != null) continue;
            String typeId = type.getId();

            int dbReserved = type.getReservedQuantity() != null ? type.getReservedQuantity() : 0;
            String redisReservedRaw = redisTemplate.opsForValue().get(String.format(KEY_TYPE_RESERVED, showId, typeId));
            int redisReserved = redisReservedRaw != null ? Integer.parseInt(redisReservedRaw) : 0;

            if (dbReserved != redisReserved) {
                log.info("[RECONCILIATION][FAST-CHECK] Nghi vấn lệch TicketType {}: DB={}, Redis={}", typeId, dbReserved, redisReserved);
                return true;
            }

            if (type.getTicketTiers() != null) {
                for (TicketTier tier : type.getTicketTiers()) {
                    if (tier.getDeletedAt() != null) continue;
                    String tierId = tier.getId();

                    int tierDbReserved = tier.getReservedQuantity() != null ? tier.getReservedQuantity() : 0;
                    String tierRedisReservedRaw = redisTemplate.opsForValue().get(String.format(KEY_TIER_RESERVED, showId, tierId));
                    int tierRedisReserved = tierRedisReservedRaw != null ? Integer.parseInt(tierRedisReservedRaw) : 0;

                    if (tierDbReserved != tierRedisReserved) {
                        log.info("[RECONCILIATION][FAST-CHECK] Nghi vấn lệch TicketTier {}: DB={}, Redis={}", tierId, tierDbReserved, tierRedisReserved);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void syncShowIfStillMismatched(Show show) {
        String showId = show.getId();
        boolean hasRepaired = false;

        for (TicketType type : show.getTicketTypes()) {
            if (type.getDeletedAt() != null) continue;
            String typeId = type.getId();

            TicketType freshType = ticketTypeRepository.findTicketTypeById(typeId);
            if (freshType == null) continue;
            int dbReserved = freshType.getReservedQuantity() != null ? freshType.getReservedQuantity() : 0;

            String redisReservedRaw = redisTemplate.opsForValue().get(String.format(KEY_TYPE_RESERVED, showId, typeId));
            int redisReserved = redisReservedRaw != null ? Integer.parseInt(redisReservedRaw) : 0;

            if (dbReserved != redisReserved) {
                log.warn("[RECONCILIATION][SYNC] XÁC NHẬN LỆCH THẬT TicketType {}: DB={}, Redis={}. Đang ghi đè Redis từ DB...",
                        typeId, dbReserved, redisReserved);
                redisTemplate.opsForValue().set(String.format(KEY_TYPE_RESERVED, showId, typeId), String.valueOf(dbReserved));
                redisTemplate.opsForValue().set(String.format(KEY_TYPE_TOTAL, showId, typeId), String.valueOf(freshType.getTotalQuantity()));
                hasRepaired = true;
            }

            if (freshType.getTicketTiers() != null) {
                for (TicketTier tier : freshType.getTicketTiers()) {
                    if (tier.getDeletedAt() != null) continue;
                    String tierId = tier.getId();

                    TicketTier freshTier = ticketTierRepository.findById(tierId).orElse(null);
                    if (freshTier == null) continue;
                    int tierDbReserved = freshTier.getReservedQuantity() != null ? freshTier.getReservedQuantity() : 0;

                    String tierRedisReservedRaw = redisTemplate.opsForValue().get(String.format(KEY_TIER_RESERVED, showId, tierId));
                    int tierRedisReserved = tierRedisReservedRaw != null ? Integer.parseInt(tierRedisReservedRaw) : 0;

                    if (tierDbReserved != tierRedisReserved) {
                        log.warn("[RECONCILIATION][SYNC] XÁC NHẬN LỆCH THẬT TicketTier {}: DB={}, Redis={}. Đang ghi đè Redis từ DB...",
                                tierId, tierDbReserved, tierRedisReserved);
                        redisTemplate.opsForValue().set(String.format(KEY_TIER_RESERVED, showId, tierId), String.valueOf(tierDbReserved));
                        redisTemplate.opsForValue().set(String.format(KEY_TIER_LIMIT, showId, tierId), String.valueOf(freshTier.getLimitQuantity()));
                        hasRepaired = true;
                    }
                }
            }
        }

        if (!hasRepaired) {
            log.info("[RECONCILIATION] Không có sự sai lệch thật sự tại Show {} (Báo động giả do in-flight request). Giữ nguyên trạng thái.", showId);
        } else {
            log.info("[RECONCILIATION] Đã hoàn tất hiệu chỉnh đồng bộ tồn kho cho Show {}", showId);
        }
    }
}

