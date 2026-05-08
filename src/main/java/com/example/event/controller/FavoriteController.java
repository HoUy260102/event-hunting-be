package com.example.event.controller;

import com.example.event.dto.response.ApiResponse;
import com.example.event.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService favoriteService;

    @PostMapping("/{eventId}")
    public ResponseEntity<?> add(@PathVariable String eventId) {
        favoriteService.addFavorite(eventId);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<?> remove(@PathVariable String eventId) {
        favoriteService.removeFavorite(eventId);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.NO_CONTENT.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
    }
}
