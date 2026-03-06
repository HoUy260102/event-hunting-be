package com.example.event.dto.request;

import com.example.event.entity.Role;
import com.example.event.validation.Phone;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
public class SignUpReq {
    @NotBlank(message = "Email không được trống")
    @Email(message = "Email không hợp lệ")
    private String email;
    @NotBlank(message = "Password không được trống")
    private String password;
    @NotBlank(message = "Password không được trống")
    private String confirmPassword;
    @NotBlank(message = "Tên không được trống")
    private String name;
    @Phone
    private String phone;
    private String address;
    private Date dob;
}
