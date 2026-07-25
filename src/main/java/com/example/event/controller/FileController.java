package com.example.event.controller;

import com.example.event.constant.FileFolder;
import com.example.event.constant.FileType;
import com.example.event.dto.FileDTO;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

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

    @GetMapping("/generate-signature")
    public ResponseEntity<?> generateUploadSignature() {
        Map<String, Object> res = fileService.generateUploadSignature();
        ApiResponse response = ApiResponse.builder()
                .message("Thành công.")
                .status(HttpStatus.CREATED.value())
                .data(res)
                .build();
        return ResponseEntity.status(HttpStatus.OK.value()).body(response);
    }
}
