package com.example.event.repository;

import com.example.event.constant.ReservationStatus;
import com.example.event.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
    List<Reservation> findAllByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime expiresAt);
    Reservation findReservationById(String id);
}
