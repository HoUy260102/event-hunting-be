package com.example.event.controller;

import com.example.event.config.security.SecurityUtils;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.SearchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search/history")
@RequiredArgsConstructor
public class SearchHistoryController {
    private final SearchHistoryService searchHistoryService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<?> getHistory() {
        String userId = securityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse.builder()
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .message("Vui lòng đăng nhập.")
                    .build()
            );
        }
        List<String> history = searchHistoryService.getSearchHistory(userId);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(history)
                .build());
    }

    @PostMapping
    public ResponseEntity<?> saveHistory(@RequestParam String query) {
        String userId = securityUtils.getCurrentUserId();
        if (userId != null) {
            searchHistoryService.saveSearchQuery(userId, query);
        }
        return ResponseEntity.ok(ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build());
    }

    @DeleteMapping
    public ResponseEntity<?> deleteHistory(@RequestParam(required = false) String query) {
        String userId = securityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse.builder()
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .message("Vui lòng đăng nhập.")
                    .build()
            );
        }
        if (query != null) {
            searchHistoryService.deleteSearchQuery(userId, query);
        } else {
            searchHistoryService.clearSearchHistory(userId);
        }
        return ResponseEntity.ok(ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build());
    }
}
