package com.example.event.controller;

import com.example.event.dto.VoucherDTO;
import com.example.event.dto.request.CreateVoucherReq;
import com.example.event.dto.request.SearchVoucherReq;
import com.example.event.dto.request.UpdateVoucherReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vouchers")
@RequiredArgsConstructor
public class VoucherController {
    private final VoucherService voucherService;

    @GetMapping("/{id}")
    public ResponseEntity<?> findVoucherById(@PathVariable String id) {
        VoucherDTO voucherDTO = voucherService.findVoucherById(id);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(voucherDTO)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('VOUCHER:VIEW')")
    public ResponseEntity<?> searchCategory(@Valid SearchVoucherReq req) {
        Page<VoucherDTO> voucherDTOS = voucherService.getVouchersSearch(req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(voucherDTOS)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('VOUCHER:VIEW')")
    public ResponseEntity<?> searchVoucherForMe(@Valid SearchVoucherReq req) {
        Page<VoucherDTO> voucherDTOS = voucherService.getVouchersSearchForMe(req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(voucherDTOS)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VOUCHER:CREATE')")
    public ResponseEntity<?> createVoucher(@Valid @RequestBody CreateVoucherReq req) {
        VoucherDTO voucherDTO = voucherService.createVoucher(req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message("Thành công.")
                .data(voucherDTO)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VOUCHER:UPDATE')")
    public ResponseEntity<?> updateVoucher(@Valid @RequestBody UpdateVoucherReq req, @PathVariable String id) {
        VoucherDTO voucherDTO = voucherService.updateVoucher(id, req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message("Thành công.")
                .data(voucherDTO)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PatchMapping("/{id}/soft-delete")
    @PreAuthorize("hasAuthority('VOUCHER:DELETE')")
    public ResponseEntity<?> deleteVoucher(@PathVariable String id) {
        voucherService.deleteVoucher(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('VOUCHER:RESTORE')")
    public ResponseEntity<?> restoreVoucher(@PathVariable String id) {
        voucherService.restoreVoucher(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
