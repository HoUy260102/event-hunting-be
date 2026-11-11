package com.example.event.controller;

import com.example.event.dto.response.ApiResponse;
import com.example.event.service.QdrantDataMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/search-engine")
@RequiredArgsConstructor
@Slf4j
public class AdminSearchEngineController {

    private final QdrantDataMigrationService qdrantDataMigrationService;

    @PostMapping("/reindex")
    @PreAuthorize("hasAnyRole('ADMIN') or hasAuthority('EVENT:ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerReindex() {
        log.info("[ADMIN-SEARCH-ENGINE] Quản trị viên kích hoạt reindex toàn bộ dữ liệu sự kiện sang Qdrant");
        int count = qdrantDataMigrationService.reindexAllPublishedEvents();

        ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                .status(HttpStatus.OK.value())
                .message("Đồng bộ dữ liệu sang Qdrant hoàn tất.")
                .data(Map.of("totalIndexed", count))
                .build();

        return ResponseEntity.ok(response);
    }
}

