package com.example.event.job;

import com.example.event.constant.ReservationStatus;
import com.example.event.entity.Reservation;
import com.example.event.repository.ReservationRepository;
import com.example.event.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationCleanupJob {
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> reservations = reservationRepository
                .findAllByStatusAndExpiresAtBefore(ReservationStatus.PENDING, now);
        if (reservations.isEmpty()) return;
        log.info("Phát hiện {} đơn hàng hết hạn", reservations.size());
        for (Reservation reservation : reservations) {
            try {
                reservationService.releaseReservationResources(reservation, now, ReservationStatus.EXPIRED, "cleanup");
            } catch (Exception e) {
                log.error("Lỗi khi giải phóng đơn hàng: {}", reservation.getId(), e);
            }
        }
    }
}
