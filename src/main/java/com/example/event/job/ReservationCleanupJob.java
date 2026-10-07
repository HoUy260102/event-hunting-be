package com.example.event.job;

import com.example.event.service.ReservationCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationCleanupJob {
    private final ReservationCleanupService reservationCleanupService;
    private final RedissonClient redissonClient;

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredReservations() {
        RLock lock = redissonClient.getLock("job:reservation-cleanup");
        if (!lock.tryLock()) {
            log.debug("Bỏ qua cleanup reservation vì server khác đang xử lý");
            return;
        }

        try {
            reservationCleanupService.cleanupExpiredReservationsBatch();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
