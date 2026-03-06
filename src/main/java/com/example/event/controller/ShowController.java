package com.example.event.controller;

import com.example.event.dto.request.UpdateShowStatusReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.ShowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
public class ShowController {
    private final ShowService showService;
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody UpdateShowStatusReq status) {
        showService.updateShowStatus(id, status.getStatus());
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
