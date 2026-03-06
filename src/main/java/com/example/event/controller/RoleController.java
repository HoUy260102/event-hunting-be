package com.example.event.controller;

import com.example.event.dto.PermissionDTO;
import com.example.event.dto.RoleDTO;
import com.example.event.dto.request.CreateRoleReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.PermissionService;
import com.example.event.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/roles")
public class RoleController {
    private final RoleService roleService;
    private final PermissionService permissionService;

    @GetMapping("/select")
    public ResponseEntity<?> getAllRoleForSelect() {
        List<Map<String, String>> roles = roleService.getAllRoleForSelect();
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(roles)
                .message("Thành công.")
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping()
    public ResponseEntity<?> createRole(@RequestBody CreateRoleReq req) {
        RoleDTO roleDTO = roleService.createRole(req);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .data(roleDTO)
                .message("Tạo thành công.")
                .build();
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<?> updatePermissions(@PathVariable("id") String id, @RequestBody List<String> permissionIds) {
        roleService.updatePermissions(id, permissionIds);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật thành công.")
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<?> getAllPermissionsByRoleId(@PathVariable("id") String id) {
        List<PermissionDTO> permissionDTOs = permissionService.getPermissionsByRoleId(id);
        ApiResponse response = ApiResponse.builder()
                .message("Thành công.")
                .status(HttpStatus.OK.value())
                .data(permissionDTOs)
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
