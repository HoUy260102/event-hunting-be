package com.example.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePermissionReq {
    @NotBlank(message = "Code không được trống")
    private String code;
    @NotBlank(message = "Name không được trống")
    private String name;
    @NotBlank(message = "Module không được trống")
    private String module;
}
