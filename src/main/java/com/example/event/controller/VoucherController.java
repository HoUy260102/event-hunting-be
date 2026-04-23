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
    public ResponseEntity<?> searchCategory(@Valid SearchVoucherReq req) {
        Page<VoucherDTO> voucherDTOS = voucherService.getVouchersSearch(req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(voucherDTOS)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PostMapping
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
    public ResponseEntity<?> deleteVoucher(@PathVariable String id) {
        voucherService.deleteVoucher(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<?> restoreVoucher(@PathVariable String id) {
        voucherService.restoreVoucher(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
