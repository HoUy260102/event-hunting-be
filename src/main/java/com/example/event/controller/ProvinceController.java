package com.example.event.controller;

import com.example.event.dto.ProvinceDTO;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.ProvinceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/provinces")
public class ProvinceController {
    private final ProvinceService provinceService;

    @GetMapping()
    public ResponseEntity<?> findAllProvinces() {
        List<ProvinceDTO> provinceDTOS = provinceService.findAllProvinces();
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(provinceDTOS)
                .build();
        return ResponseEntity.status(HttpStatus.OK.value()).body(response);
    }
}
