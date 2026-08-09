package com.example.event.service;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.CategoryStatus;
import com.example.event.constant.ErrorCode;
import com.example.event.dto.CategoryDTO;
import com.example.event.dto.request.CreateCategoryReq;
import com.example.event.dto.request.UpdateCategoryReq;
import com.example.event.entity.Category;
import com.example.event.exception.AppException;
import com.example.event.mapper.CategoryMapper;
import com.example.event.repository.CategoryRepository;
import com.example.event.service.Impl.CategoryServiceImpl;
import com.example.event.util.StringUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceImplTest {

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private SecurityUtils securityUtils;

    private Category activeCategory;
    private Category inactiveCategory;
    private Category deletedCategory;

    @BeforeEach
    void setUp() {
        activeCategory = new Category();
        activeCategory.setId("cat-1");
        activeCategory.setName("Âm nhạc");
        activeCategory.setSlug("am-nhac");
        activeCategory.setStatus(CategoryStatus.ACTIVE);
        activeCategory.setDeletedAt(null);

        inactiveCategory = new Category();
        inactiveCategory.setId("cat-2");
        inactiveCategory.setName("Thể thao");
        inactiveCategory.setSlug("the-thao");
        inactiveCategory.setStatus(CategoryStatus.INACTIVE);
        inactiveCategory.setDeletedAt(null);

        deletedCategory = new Category();
        deletedCategory.setId("cat-3");
        deletedCategory.setName("Ẩm thực");
        deletedCategory.setSlug("am-thuc");
        deletedCategory.setStatus(CategoryStatus.ACTIVE);
        deletedCategory.setDeletedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("findAllCategories should return only active and not deleted categories")
    void findAllCategories_ShouldReturnOnlyVisibleCategories() {
        when(categoryRepository.findAll()).thenReturn(new ArrayList<>(Arrays.asList(activeCategory, inactiveCategory, deletedCategory)));
        when(categoryMapper.toDTO(activeCategory)).thenReturn(CategoryDTO.builder()
                .id("cat-1")
                .name("Âm nhạc")
                .slug("am-nhac")
                .status(CategoryStatus.ACTIVE)
                .build());

        List<CategoryDTO> result = categoryService.findAllCategories();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("cat-1", result.get(0).getId());
        assertEquals("Âm nhạc", result.get(0).getName());
        assertEquals(CategoryStatus.ACTIVE, result.get(0).getStatus());
    }

    @Test
    void findCategoryById_Success() {
        //Given
        Category category = new Category();
        category.setId("cat-1");
        category.setName("Âm nhạc");
        category.setSlug("am-nhac");
        category.setStatus(CategoryStatus.ACTIVE);

        CategoryDTO categoryDTO = CategoryDTO.builder()
                .id("cat-1")
                .name("Âm nhạc")
                .slug("am-nhac")
                .status(CategoryStatus.ACTIVE)
                .build();

        //When
        when(categoryRepository.findCategoryById(any()))
                .thenReturn(category);
        when(categoryMapper.toDTO(any()))
                .thenReturn(categoryDTO);
        CategoryDTO result = categoryService.findCategoryById("cat-1");

        // Then
        assertNotNull(result);
        assertEquals("cat-1", result.getId());
        assertEquals("Âm nhạc", result.getName());
        assertEquals(CategoryStatus.ACTIVE, result.getStatus());
        assertEquals("am-nhac", result.getSlug());
    }

    @Test
    void findCategoryById_Failure() {
        //Given
        Category category = new Category();
        category.setId("cat-1");
        category.setName("Âm nhạc");
        category.setSlug("am-nhac");
        category.setStatus(CategoryStatus.ACTIVE);

        CategoryDTO categoryDTO = CategoryDTO.builder()
                .id("cat-1")
                .name("Âm nhạc")
                .slug("am-nhac")
                .status(CategoryStatus.ACTIVE)
                .build();

        // When
        when(categoryRepository.findCategoryById(any()))
                .thenReturn(null);
        when(categoryMapper.toDTO(any()))
                .thenReturn(categoryDTO);
        AppException exception = assertThrows(
                AppException.class,
                () -> categoryService.findCategoryById("cat-1")
        );

        // Then
        assertEquals(exception.getErrorCode(), ErrorCode.CATEGORY_NOT_FOUND);
        assertEquals(exception.getErrorCode().getHttpStatus(), HttpStatus.NOT_FOUND);
    }

    @Test
    void createCategory_Success() {
        //Given
        CreateCategoryReq req = new CreateCategoryReq();
        req.setName("Âm nhạc");
        req.setStatus(CategoryStatus.ACTIVE);

        //When
        when(securityUtils.getCurrentUserId())
                .thenReturn("user-1");
        when(categoryRepository.existsCategoryByName(any()))
                .thenReturn(false);
        when(categoryRepository.existsCategoryBySlug(any()))
                .thenReturn(false);
        when(categoryMapper.toDTO(any(Category.class)))
                .thenReturn(CategoryDTO.builder()
                        .id("cat-1")
                        .name("Âm nhạc")
                        .slug("am-nhac")
                        .status(CategoryStatus.ACTIVE)
                        .build());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> {
                    Category category = invocation.getArgument(0, Category.class);
                    category.setId("cat-1");
                    return category;
                });
        var result = categoryService.createCategory(req);

        //Then
        assertNotNull(result);
        assertEquals("cat-1", result.getId());
        assertEquals("Âm nhạc", result.getName());
        assertEquals("am-nhac", result.getSlug());
        assertEquals(CategoryStatus.ACTIVE, result.getStatus());
        verify(categoryRepository, times(1)).save(any(Category.class));

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());

        Category savedCategory = categoryCaptor.getValue();
        assertEquals("cat-1", savedCategory.getId());
        assertEquals("Âm nhạc", savedCategory.getName());
        assertEquals("am-nhac", savedCategory.getSlug());
        assertEquals(CategoryStatus.ACTIVE, savedCategory.getStatus());
        assertEquals("user-1", savedCategory.getCreatedBy());
        assertNotNull(savedCategory.getCreatedAt());
    }

    @Test
    @DisplayName("Tạo category thất bại - Tên category đã tồn tại")
    void createCategory_WhenNameExists_ShouldThrowException() {
        // Given
        CreateCategoryReq req = new CreateCategoryReq();
        req.setName("Âm nhạc");
        req.setStatus(CategoryStatus.ACTIVE);

        when(categoryRepository.existsCategoryByName("Âm nhạc")).thenReturn(true);

        // When
        when(securityUtils.getCurrentUserId())
                .thenReturn("user-1");

        AppException exception = assertThrows(
                AppException.class,
                () -> categoryService.createCategory(req)
        );

        // Then
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals(exception.getDetails().get("name"), "Tên chủ đề này đã tồn tại trên hệ thống!");
    }

    @Test
    @DisplayName("Tạo category thất bại - Tên category đã tồn tại")
    void createCategory_WhenSlugExists_ShouldThrowException() {
        // GIVEN
        CreateCategoryReq req = new CreateCategoryReq();
        req.setName("Âm nhạc");
        req.setStatus(CategoryStatus.ACTIVE);

        when(categoryRepository.existsCategoryByName("Âm nhạc")).thenReturn(false);
        when(categoryRepository.existsCategoryBySlug("am-nhac")).thenReturn(true);

        // WHEN & THEN
        when(securityUtils.getCurrentUserId())
                .thenReturn("user-1");

        AppException exception = assertThrows(
                AppException.class,
                () -> categoryService.createCategory(req)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals(exception.getDetails().get("name"), "Tên chủ đề này đã tồn tại trên hệ thống!");
    }

    @Test
    void updateCategory_Success() {
        //Given
        UpdateCategoryReq req = new UpdateCategoryReq();
        req.setName("Thể thao");
        req.setStatus(CategoryStatus.ACTIVE);

        Category existingCategory = new Category();
        existingCategory.setId("cat-1");
        existingCategory.setName("Âm nhạc");
        existingCategory.setSlug("am-nhac");
        existingCategory.setStatus(CategoryStatus.ACTIVE);

        //When
        when(securityUtils.getCurrentUserId())
                .thenReturn("user-1");
        when(categoryRepository.findCategoryById(any()))
                .thenReturn(existingCategory);
        when(categoryRepository.existsCategoryBySlugAndIdNot(any(), any()))
                .thenReturn(false);
        when(categoryRepository.existsCategoryByNameAndIdNot(any(), any()))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryMapper.toDTO(any(Category.class)))
                .thenReturn(CategoryDTO.builder()
                        .id("cat-1")
                        .name("Thể thao")
                        .slug("the-thao")
                        .status(CategoryStatus.ACTIVE)
                        .build());
        var result = categoryService.updateCategory("cat-1", req);

        //Then
        assertNotNull(result);
        assertEquals("cat-1", result.getId());
        assertEquals("Thể thao", result.getName());
        assertEquals("the-thao", result.getSlug());
        assertEquals(CategoryStatus.ACTIVE, result.getStatus());
        verify(categoryRepository, times(1)).save(existingCategory);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());

        Category savedCategory = categoryCaptor.getValue();
        assertEquals("Thể thao", savedCategory.getName());
        assertEquals("the-thao", savedCategory.getSlug());
        assertEquals("user-1", savedCategory.getUpdatedBy());
        assertNotNull(savedCategory.getUpdatedAt());
    }

    @Test
    void updateCategory_ShouldThrowAppException_WhenStatusIsDeleted() {
        //Given
        String catId = "cat-1";
        UpdateCategoryReq req = new UpdateCategoryReq();
        req.setName("Âm nhạc");
        req.setStatus(CategoryStatus.DELETED);

        //When
        AppException exception = assertThrows(
                AppException.class,
                () -> categoryService.updateCategory(catId, req)
        );

        //Then
        assertEquals(ErrorCode.CATEGORY_STATUS_INVALID, exception.getErrorCode());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_ShouldThrowAppException_WhenCategoryNotFound() {
        //Given
        String catId = "cat-1";
        UpdateCategoryReq req = new UpdateCategoryReq();
        req.setName("Thể thao");
        req.setStatus(CategoryStatus.ACTIVE);

        //When
        when(securityUtils.getCurrentUserId())
                .thenReturn("user-1");
        when(categoryRepository.findCategoryById(any()))
                .thenReturn(null);
        AppException exception = assertThrows(
                AppException.class,
                () -> categoryService.updateCategory(catId, req)
        );

        //Then
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, exception.getErrorCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_ShouldThrowAppException_WhenCategorySoftDeleted() {
        //Given
        String catId = "cat-1";
        UpdateCategoryReq req = new UpdateCategoryReq();
        req.setName("Thể thao");
        req.setStatus(CategoryStatus.ACTIVE);

        Category exsistingCategory = new Category();
        exsistingCategory.setName("Âm nhạc");
        exsistingCategory.setSlug("am-nhac");
        exsistingCategory.setDeletedAt(LocalDateTime.now().minusDays(2));

        //When
        when(securityUtils.getCurrentUserId())
                .thenReturn("user-1");
        when(categoryRepository.findCategoryById(any()))
                .thenReturn(exsistingCategory);
        AppException exception = assertThrows(
                AppException.class,
                () -> categoryService.updateCategory(catId, req)
        );

        //Then
        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, exception.getErrorCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nên ném VALIDATION_ERROR khi tên hoặc slug bị trùng với danh mục khác")
    void updateCategory_ShouldThrowAppException_WhenNameOrSlugAlreadyExists() {
        String catId = "cat-1";
        String newName = "Thể thao";
        String newSlug = StringUtil.makeSlug(newName); // Giả sử sinh ra "the-thao"

        UpdateCategoryReq req = new UpdateCategoryReq();
        req.setName(newName);
        req.setStatus(CategoryStatus.ACTIVE);

        Category existingCategory = new Category();
        existingCategory.setId(catId);
        existingCategory.setName("Âm nhạc");
        existingCategory.setSlug("am-nhac");
        existingCategory.setDeletedAt(null);

        when(securityUtils.getCurrentUserId())
                .thenReturn("user-1");
        when(categoryRepository.findCategoryById(catId))
                .thenReturn(existingCategory);
        when(categoryRepository.existsCategoryBySlugAndIdNot(newSlug, catId))
                .thenReturn(false);
        when(categoryRepository.existsCategoryByNameAndIdNot(newName, catId))
                .thenReturn(true);
        AppException exception = assertThrows(
                AppException.class,
                () -> categoryService.updateCategory(catId, req)
        );

        //Then
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertNotNull(exception.getDetails());
        assertTrue(exception.getDetails().containsKey("name"));
        assertEquals("Tên chủ đề này đã tồn tại trên hệ thống!", exception.getDetails().get("name"));
        verify(categoryRepository, never()).save(any());
    }
}
