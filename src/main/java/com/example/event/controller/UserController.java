package com.example.event.controller;

import com.example.event.dto.UserDTO;
import com.example.event.dto.request.CreateUserReq;
import com.example.event.dto.request.UpdateUserReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.awt.print.Pageable;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/search")
    public ResponseEntity<?> searchUser(@RequestParam String keyword, @RequestParam int page, @RequestParam int size, @RequestParam String roleId, @RequestParam String status) {
        Page<UserDTO> userDtos = userService.getUserSearch(keyword, roleId, status, page - 1, size);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(userDtos)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER:CREATE')")
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserReq req) {
        UserDTO userDTO = userService.createUser(req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message("Thành công.")
                .data(userDTO)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER:UPDATE')")
    public ResponseEntity<?> updateUser(@Valid @RequestBody UpdateUserReq req, @PathVariable String id) {
        UserDTO userDTO = userService.updateUser(req, id);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(userDTO)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> findUserById(@PathVariable String id) {
        UserDTO userDTO = userService.findUserById(id);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(userDTO)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PatchMapping("/{id}/soft-delete")
    @PreAuthorize("hasAuthority('USER:DELETE')")
    public ResponseEntity<?> softDeleteUser(@PathVariable String id) {
        UserDTO userDTO = userService.softDeleteUser(id);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(userDTO)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('USER:RESTORE')")
    public ResponseEntity<?> restoreUser(@PathVariable String id) {
        UserDTO userDTO = userService.restoreUser(id);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(userDTO)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}
