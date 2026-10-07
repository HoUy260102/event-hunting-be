package com.example.event.service.Impl;

import com.example.event.constant.ReservationStatus;
import com.example.event.entity.Reservation;
import com.example.event.repository.ReservationRepository;
import com.example.event.service.ReservationCleanupService;
import com.example.event.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationCleanupServiceImpl implements ReservationCleanupService {
    private static final int BATCH_SIZE = 100;

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @Override
    @Transactional
    public void cleanupExpiredReservationsBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> reservations = reservationRepository.findAllByStatusAndExpiresAtBefore(
                ReservationStatus.PENDING,
                now,
                PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Order.asc("expiresAt"), Sort.Order.asc("id"))));
        if (reservations.isEmpty()) return;

        log.info("Phát hiện {} đơn hàng hết hạn trong batch", reservations.size());
        for (Reservation reservation : reservations) {
            try {
                reservationService.releaseReservationResources(reservation, now, ReservationStatus.EXPIRED, "cleanup");
            } catch (Exception e) {
                log.error("Lỗi khi giải phóng đơn hàng: {}", reservation.getId(), e);
            }
        }
    }
}
