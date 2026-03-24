package com.example.event.repository;

import com.example.event.constant.ReservationStatus;
import com.example.event.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
    List<Reservation> findAllByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime expiresAt);
    @Query("""
        select r from Reservation r
        left join fetch r.event
        left join fetch r.show
        where r.id = :reservationId and r.deletedAt is null and r.status = 'PAID'
    """)
    Reservation findReservationDetailById(@Param("reservationId") String reservationId);
    Reservation findReservationById(String id);
}
