package com.example.event.controller;

import com.example.event.constant.FileFolder;
import com.example.event.constant.FileType;
import com.example.event.dto.FileDTO;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {
    private final FileService fileService;
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") FileType type,
            @RequestParam("folder") FileFolder folder) {
        FileDTO fileDTO = fileService.uploadFile(file, type, folder);
        ApiResponse response = ApiResponse.builder()
                .message("Thành công")
                .status(HttpStatus.CREATED.value())
                .data(fileDTO)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED.value()).body(response);
    }
}
