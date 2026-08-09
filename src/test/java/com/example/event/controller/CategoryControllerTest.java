package com.example.event.controller;

import com.example.event.config.security.jwt.JwtTokenFilter;
import com.example.event.constant.CategoryStatus;
import com.example.event.dto.CategoryDTO;
import com.example.event.dto.request.CreateCategoryReq;
import com.example.event.filter.RateLimit;
import com.example.event.filter.UserRateLimit;
import com.example.event.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(CategoryController.class)
@Import(CategoryControllerTest.TestSecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenFilter jwtTokenFilter;

    @MockBean
    private RateLimit rateLimit;

    @MockBean
    private UserRateLimit userRateLimit;

    @MockBean
    private CategoryService categoryService;

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

            return http.build();
        }
    }

    @Test
    void findAllCategories_ShouldReturnList() throws Exception {
        CategoryDTO category = CategoryDTO.builder()
                .id("cat-1")
                .name("Âm nhạc")
                .slug("am-nhac")
                .description("Sự kiện âm nhạc")
                .status(CategoryStatus.ACTIVE)
                .build();

        when(categoryService.findAllCategories()).thenReturn(Arrays.asList(category));

        mockMvc.perform(get("/categories")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Thành công."))
                .andExpect(jsonPath("$.data[0].id").value("cat-1"))
                .andExpect(jsonPath("$.data[0].name").value("Âm nhạc"))
                .andExpect(jsonPath("$.data[0].slug").value("am-nhac"));
    }

    @Test
    void findCategoryById_Success() throws Exception {
        String catId = "cat-1";

        CategoryDTO category = CategoryDTO.builder()
                .id("cat-1")
                .name("Âm nhạc")
                .slug("am-nhac")
                .description("Sự kiện âm nhạc")
                .status(CategoryStatus.ACTIVE)
                .build();

        when(categoryService.findCategoryById(catId)).thenReturn(category);

        mockMvc.perform(get("/categories/cat-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Thành công."))
                .andExpect(jsonPath("$.data.id").value("cat-1"))
                .andExpect(jsonPath("$.data.name").value("Âm nhạc"))
                .andExpect(jsonPath("$.data.slug").value("am-nhac"));
    }

    @Test
    void createCategory_withValid_returnsCreatedCategory() throws Exception {
        // Given
        CreateCategoryReq req = new CreateCategoryReq();
        req.setName("Âm nhạc");
        req.setStatus(CategoryStatus.ACTIVE);

        CategoryDTO createdCategory = CategoryDTO.builder()
                .id("cat-1")
                .name("Âm nhạc")
                .slug("am-nhac")
                .status(CategoryStatus.ACTIVE)
                .build();

        // When
        when(categoryService.createCategory(any()))
                .thenReturn(createdCategory);

        //Then
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("cat-1"))
                .andExpect(jsonPath("$.data.name").value("Âm nhạc"))
                .andExpect(jsonPath("$.data.status").value(CategoryStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.data.slug").value("am-nhac"));
        verify(categoryService, times(1)).createCategory(any(CreateCategoryReq.class));
    }
}
