package com.example.event.controller;

import com.example.event.dto.ReservationDTO;
import com.example.event.dto.ReservationDetailDTO;
import com.example.event.dto.ReservationSummaryDTO;
import com.example.event.dto.request.ReservationReq;
import com.example.event.dto.request.SearchReservationReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/{id}/vouchers")
    public ResponseEntity<?> findReservationAfterDiscount(@PathVariable String id, @RequestParam(required = false) String voucherId) {
        ReservationDTO reservationDTO = reservationService.findReservationAfterDiscount(id, voucherId);
        ApiResponse response = ApiResponse.builder()
                .data(reservationDTO)
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
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

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('RESERVATION:VIEW')")
    public ResponseEntity<?> searchReservation(@Valid SearchReservationReq req) {
        Page<ReservationDetailDTO> reservationDTOS = reservationService.getReservationsSearch(req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(reservationDTOS)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('RESERVATION:VIEW')")
    public ResponseEntity<?> searchReservationForMe(@Valid SearchReservationReq req) {
        Page<ReservationDetailDTO> reservationDTOS = reservationService.getReservationsSearchForMe(req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(reservationDTOS)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> findReservationById(@PathVariable String id) {
        ReservationSummaryDTO reservationDTO = reservationService.findReservationById(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(reservationDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasAuthority('RESERVATION:VIEW')")
    public ResponseEntity<?> findReservationSummaryById(@PathVariable String id) {
        ReservationSummaryDTO reservationDTO = reservationService.findReservationSummaryById(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(reservationDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
