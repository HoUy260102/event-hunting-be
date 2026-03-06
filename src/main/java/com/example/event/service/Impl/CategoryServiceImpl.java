package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.CategoryStatus;
import com.example.event.constant.ErrorCode;
import com.example.event.dto.CategoryDTO;
import com.example.event.dto.request.CategorySearchReq;
import com.example.event.dto.request.CreateCategoryReq;
import com.example.event.dto.request.UpdateCategoryReq;
import com.example.event.entity.Category;
import com.example.event.exception.AppException;
import com.example.event.mapper.CategoryMapper;
import com.example.event.repository.CategoryRepository;
import com.example.event.service.CategoryService;
import com.example.event.specification.CategorySpecifiation;
import com.example.event.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final SecurityUtils securityUtils;

    @Override
    public List<CategoryDTO> findAllCategories() {
        return categoryRepository.findAll().stream()
                .filter(category -> category.getStatus() == CategoryStatus.ACTIVE && category.getDeletedAt() == null)
                .map(categoryMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CategoryDTO createCategory(CreateCategoryReq req) {
        String creatorId = securityUtils.getCurrentUserId();
        String slug = StringUtil.makeSlug(req.getName());
        if (categoryRepository.existsCategoryByName(req.getName()) || categoryRepository.existsCategoryBySlug(slug)) {
            throw new AppException(ErrorCode.CATEGORY_EXISTS);
        }
        Category category = new Category();
        category.setName(req.getName());
        category.setSlug(slug);
        category.setStatus(req.getStatus());
        category.setDescription(req.getDescription());
        category.setCreatedAt(LocalDateTime.now());
        category.setCreatedBy(creatorId);
        category.setUpdatedAt(LocalDateTime.now());
        category.setUpdatedBy(creatorId);
        categoryRepository.save(category);
        return categoryMapper.toDTO(category);
    }

    @Override
    @Transactional
    public CategoryDTO updateCategory(String id, UpdateCategoryReq req) {
        if (req.getStatus() == CategoryStatus.DELETED) {
            throw new AppException(ErrorCode.CATEGORY_STATUS_INVALID);
        }
        String updatorId = securityUtils.getCurrentUserId();
        Category category = Optional.ofNullable(categoryRepository.findCategoryById(id))
                        .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        if (category.getDeletedAt() != null) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        if (req.getName() != null && !req.getName().equals("") && !category.getName().equals(req.getName())) {
            String newSlug = StringUtil.makeSlug(req.getName());
            if (categoryRepository.existsCategoryByNameAndIdNot(req.getName(), id) ||
                    categoryRepository.existsCategoryBySlugAndIdNot(newSlug, id)) {
                throw new AppException(ErrorCode.CATEGORY_EXISTS);
            }
            category.setName(req.getName());
            category.setSlug(newSlug);
        }
        if (req.getDescription() != null) category.setDescription(req.getDescription());
        category.setStatus(req.getStatus());
        category.setUpdatedAt(LocalDateTime.now());
        category.setUpdatedBy(updatorId);
        categoryRepository.save(category);
        return categoryMapper.toDTO(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDTO findCategoryById(String id) {
        Category category = Optional.ofNullable(categoryRepository.findCategoryById(id))
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        return categoryMapper.toDTO(category);
    }

    @Override
    @Transactional
    public void deleteCategory(String id) {
        String deletorId = securityUtils.getCurrentUserId();
        Category category = Optional.ofNullable(categoryRepository.findCategoryById(id))
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        if (category.getDeletedAt() != null) {
            throw new AppException(ErrorCode.CATEGORY_ALREADY_DELETED);
        }
        category.setName(category.getName() + "-deleted-" + System.currentTimeMillis());
        category.setSlug(category.getSlug() + "-deleted-" + System.currentTimeMillis());
        category.setUpdatedAt(LocalDateTime.now());
        category.setUpdatedBy(deletorId);
        category.setDeletedAt(LocalDateTime.now());
        category.setDeletedBy(deletorId);
        categoryRepository.save(category);
    }

    @Override
    @Transactional
    public void restoreCategory(String id) {
        String restorId = securityUtils.getCurrentUserId();
        Category category = Optional.ofNullable(categoryRepository.findCategoryById(id))
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        if (category.getDeletedAt() == null) {
            throw new AppException(ErrorCode.CATEGORY_NOT_IN_TRASH);
        }
        String name = category.getName().replaceAll("-deleted-\\d+$", "");
        String slug = category.getSlug().replaceAll("-deleted-\\d+$", "");
        if (categoryRepository.existsCategoryByNameAndIdNot(name, id) ||
                categoryRepository.existsCategoryBySlugAndIdNot(slug, id)) {
            throw new AppException(ErrorCode.CATEGORY_EXISTS);
        }
        category.setName(name);
        category.setSlug(slug);
        category.setUpdatedAt(LocalDateTime.now());
        category.setUpdatedBy(restorId);
        category.setDeletedAt(null);
        category.setDeletedBy(null);
        categoryRepository.save(category);
    }

    @Override
    public Page<CategoryDTO> getCategorySearch(CategorySearchReq req) {
        Pageable pageable = PageRequest.of(req.getPage() - 1, req.getSize());
        Specification <Category> spec = (root, query, cb) -> cb.conjunction();
        if (req.getKeyword() != null && !req.getKeyword().isEmpty()) {
            spec = spec.and(Specification.anyOf(CategorySpecifiation.hasName(req.getKeyword()),
                    CategorySpecifiation.hasSlug(req.getKeyword()),
                    CategorySpecifiation.hasId(req.getKeyword())));
        }
        String status = req.getStatus().toUpperCase();
        switch (status) {
            case "DELETED":
                spec = spec.and(CategorySpecifiation.isDeleted());
                break;

            case "ALL":
                spec = spec.and(CategorySpecifiation.isNotDeleted());
                break;

            default:
                CategoryStatus statusEnum = CategoryStatus.valueOf(status);
                spec = spec.and(CategorySpecifiation.hasStatus(statusEnum))
                        .and(CategorySpecifiation.isNotDeleted());
                break;
        }
        Page<Category> categories = categoryRepository.findAll(spec, pageable);
        return categories.map(categoryMapper::toDTO);
    }
}
