package com.example.event.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginReq {
    @NotBlank(message = "Email không được trống")
    @Email(message = "Email không hợp lệ")
    private String username;
    @NotBlank(message = "Password không được trống")
    private String password;
}
