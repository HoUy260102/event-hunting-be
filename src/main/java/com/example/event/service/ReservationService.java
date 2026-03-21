package com.example.event.service;

import com.example.event.constant.ReservationStatus;
import com.example.event.dto.ReservationDTO;
import com.example.event.dto.request.ReservationReq;
import com.example.event.entity.Reservation;

import java.time.LocalDateTime;

public interface ReservationService {
    ReservationDTO createReservation(ReservationReq req);
    void cancelReservation(String id);
    void releaseReservationResources(Reservation reservation, LocalDateTime now, ReservationStatus status, String updatedBy);
}
