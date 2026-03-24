package com.example.event.controller;

import com.example.event.dto.ReservationDTO;
import com.example.event.dto.ReservationDetailDTO;
import com.example.event.dto.request.ReservationReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<?> createReservation(@Valid @RequestBody ReservationReq req) {
        ReservationDTO reservationDTO = reservationService.createReservation(req);
        ApiResponse response = ApiResponse.builder()
                .data(reservationDTO)
                .status(HttpStatus.CREATED.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelReservation(@PathVariable String id) {
        reservationService.cancelReservation(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/payment-success")
    public ResponseEntity<?> findReservationSuccessById(@PathVariable String id) {
        ReservationDetailDTO reservationDTO = reservationService.findReservationSuccessById(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(reservationDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
