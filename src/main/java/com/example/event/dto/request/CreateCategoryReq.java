package com.example.event.dto.request;

import com.example.event.constant.CategoryStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCategoryReq {
    @NotBlank(message = "Name không được trống")
    private String name;
    private String description;
    private CategoryStatus status = CategoryStatus.ACTIVE;
}
