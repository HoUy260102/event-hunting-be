package com.example.event.controller;

import com.example.event.dto.CategoryDTO;
import com.example.event.dto.request.CategorySearchReq;
import com.example.event.dto.request.CreateCategoryReq;
import com.example.event.dto.request.UpdateCategoryReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<?> findAllCategories() {
        List<CategoryDTO> categoryDTOs = categoryService.findAllCategories();
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(categoryDTOs)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> findCategoryById(@PathVariable String id) {
        CategoryDTO categoryDTO = categoryService.findCategoryById(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(categoryDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping
    public ResponseEntity<?> createCategory(@Valid @RequestBody CreateCategoryReq req) {
        CategoryDTO categoryDTO = categoryService.createCategory(req);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .data(categoryDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCategory(@Valid @RequestBody UpdateCategoryReq req, @PathVariable String id) {
        CategoryDTO categoryDTO = categoryService.updateCategory(id, req);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(categoryDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping("/{id}/soft-delete")
    public ResponseEntity<?> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<?> restoreCategory(@PathVariable String id) {
        categoryService.restoreCategory(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchCategory(@Valid CategorySearchReq req) {
        Page<CategoryDTO> categoryDTOS = categoryService.getCategorySearch(req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(categoryDTOS)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}
