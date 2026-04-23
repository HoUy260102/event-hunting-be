package com.example.event.controller;

import com.example.event.dto.*;
import com.example.event.dto.request.UpdateShowStatusReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.ShowService;
import com.example.event.service.TicketQueueService;
import com.example.event.service.TicketTypeService;
import com.example.event.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {
    private final ShowService showService;
    private final TicketQueueService ticketQueueService;
    private final TicketTypeService ticketTypeService;
    private final VoucherService voucherService;

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody UpdateShowStatusReq status) {
        showService.updateShowStatus(id, status.getStatus());
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/booking")
    public ResponseEntity<?> findShowBookingById(@PathVariable String id) {
        ShowBookingDTO showBookingDTO = showService.findShowBookingById(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(showBookingDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/ticket-types/selection")
    public ResponseEntity<?> findTicketTypeSelectionById(@PathVariable String id) {
        List<TicketTypeSelectionDTO> ticketTypeSelectionDTOS = ticketTypeService.findTicketTypeSelectionByShowId(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(ticketTypeSelectionDTOS)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<?> findShowDetailById(@PathVariable String id) {
        ShowDetailDTO showDetailDTO = showService.findShowDetailById(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(showDetailDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/registry")
    public ResponseEntity<?> findShowRegistryById(@PathVariable String id) {
        ShowRegistryDTO showBookingDTO = showService.findShowRegistryById(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(showBookingDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/vouchers")
    public ResponseEntity<?> findVouchersByShowId(@PathVariable String id) {
        List<VoucherDTO> voucherDTOs = voucherService.findVoucherByShowIdOrVoucherSystem(id);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message("Thành công.")
                .data(voucherDTOs)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/{id}/vouchers/search")
    public ResponseEntity<?> findVoucherByCode(@PathVariable String id, @RequestParam(name = "code") String code) {
        VoucherDTO voucherDTO = voucherService.findVoucherOfShowByCode(id, code);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(voucherDTO)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PostMapping("/{id}/queue/join")
    public ResponseEntity<?> joinQueue(@PathVariable String id) {
        Map<String, Object> result = ticketQueueService.joinQueue(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(result)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/queue/status")
    public ResponseEntity<?> pollStatus(@PathVariable String id) {
        Map<String, Object> result = ticketQueueService.getStatus(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(result)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{id}/queue/leave")
    public ResponseEntity<?> leaveQueue(@PathVariable String id) {
        ticketQueueService.leaveQueue(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/validate-access")
    public ResponseEntity<?> validateAccess(
            @PathVariable String showId,
            @RequestParam String token) {
        ticketQueueService.validateQueueToken(showId, token);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
