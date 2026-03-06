package com.example.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateRoleReq {
    @NotBlank(message = "Name không được trống")
    private String name;
    private String description;
    private List<String> permissionIds = new ArrayList<>();
}
