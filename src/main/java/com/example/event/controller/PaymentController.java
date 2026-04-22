package com.example.event.controller;

import com.example.event.dto.ReservationDTO;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.PaymentService;
import com.example.event.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final TicketService ticketService;

    @PostMapping("/create_payment_url")
    public ResponseEntity<?> createPayment(@RequestBody ReservationDTO request, HttpServletRequest httpRequest) {
        String paymentUrl = paymentService.createPaymentUrl(request, httpRequest);
        ApiResponse response = ApiResponse.builder()
                .message("Thành công")
                .status(HttpStatus.OK.value())
                .data(paymentUrl)
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/vnpay-callback")
    public void handleVNPayCallback(HttpServletRequest request) {
        Map<String, String> result = paymentService.processReturn(request);
        paymentService.processPayment(result);
    }
}
