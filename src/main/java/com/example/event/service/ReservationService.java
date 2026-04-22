package com.example.event.service;

import com.example.event.constant.ReservationStatus;
import com.example.event.dto.ReservationDTO;
import com.example.event.dto.ReservationDetailDTO;
import com.example.event.dto.ReservationSummaryDTO;
import com.example.event.dto.request.ReservationReq;
import com.example.event.entity.Reservation;
import com.example.event.entity.Voucher;

import java.time.LocalDateTime;

public interface ReservationService {
    ReservationDetailDTO findReservationSuccessById(String id);

    ReservationSummaryDTO findReservationSummaryById(String id);

    ReservationDTO createReservation(ReservationReq req);

    ReservationDTO findReservationAfterDiscount(String reservationId, String voucherId);

    void cancelReservation(String id);

    void releaseReservationResources(Reservation reservation, LocalDateTime now, ReservationStatus status, String updatedBy);

    void validateReservationForPayment(Reservation reservation);

    ReservationDTO calculateDiscount(Reservation reservation, Voucher voucher);

    void applyDiscount(Reservation reservation, Voucher voucher, ReservationDTO calculated);

    void resetDiscount(Reservation reservation);
}
