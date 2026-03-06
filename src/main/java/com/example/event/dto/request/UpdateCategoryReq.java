package com.example.event.dto.request;

import com.example.event.constant.CategoryStatus;
import lombok.Data;

@Data
public class UpdateCategoryReq {
    private String name;
    private String description;
    private CategoryStatus status = CategoryStatus.ACTIVE;
}
