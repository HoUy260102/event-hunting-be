package com.example.event.service;

import com.example.event.dto.CategoryDTO;
import com.example.event.dto.request.CategorySearchReq;
import com.example.event.dto.request.CreateCategoryReq;
import com.example.event.dto.request.UpdateCategoryReq;
import org.springframework.data.domain.Page;

import java.util.List;

public interface CategoryService {
    List<CategoryDTO> findAllCategories();
    CategoryDTO createCategory(CreateCategoryReq req);
    CategoryDTO updateCategory(String id, UpdateCategoryReq req);
    CategoryDTO findCategoryById(String id);
    void deleteCategory(String id);
    void restoreCategory(String id);
    Page<CategoryDTO> getCategorySearch(CategorySearchReq req);
}
