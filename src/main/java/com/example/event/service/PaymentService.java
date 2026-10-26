package com.example.event.service;

import com.example.event.dto.ReservationDTO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface PaymentService {
    String createPaymentUrl(ReservationDTO reservationDTO, HttpServletRequest httpRequest);
    Map<String, String> processReturn(HttpServletRequest request);
    void processPayment(Map<String, String> vnpayParams);
}