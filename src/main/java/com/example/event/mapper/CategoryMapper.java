package com.example.event.mapper;

import com.example.event.constant.CategoryStatus;
import com.example.event.dto.CategoryDTO;
import com.example.event.entity.Category;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CategoryMapper {
    private final ModelMapper modelMapper;
    public CategoryDTO toDTO(Category category) {
        CategoryDTO categoryDTO = modelMapper.map(category, CategoryDTO.class);
        if (category.getDeletedAt() != null) {
            categoryDTO.setStatus(CategoryStatus.DELETED);
        }
        return categoryDTO;
    }
}
