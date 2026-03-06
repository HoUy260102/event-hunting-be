package com.example.event.controller;

import com.example.event.dto.PermissionDTO;
import com.example.event.dto.request.CreatePermissionReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.dto.response.KeysetPageResponse;
import com.example.event.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService permissionService;

    @GetMapping("/all")
    public ResponseEntity<?> getAllPermissions() {
        List<PermissionDTO> permissionDTOs = permissionService.getAllPermissions();
        ApiResponse response = ApiResponse.builder()
                .message("Thành công")
                .status(HttpStatus.OK.value())
                .data(permissionDTOs)
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<?> createPermission(@Valid @RequestBody CreatePermissionReq req) {
        PermissionDTO permissionDTO = permissionService.createPermission(req);
        ApiResponse response = ApiResponse.builder()
                .message("Thành công")
                .status(HttpStatus.CREATED.value())
                .data(permissionDTO)
                .build();
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<?> getPermissions(@RequestParam(required = false) String keyword, @RequestParam(required = false) String nextId, @RequestParam(required = false, defaultValue = "2") int size) {
        KeysetPageResponse<PermissionDTO, String> permissionDTOs = permissionService.getPermissions(keyword, nextId, size);
        ApiResponse response = ApiResponse.builder()
                .message("Thành công")
                .status(HttpStatus.OK.value())
                .data(permissionDTOs)
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
