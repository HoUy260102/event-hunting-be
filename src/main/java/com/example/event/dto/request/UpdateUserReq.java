package com.example.event.dto.request;

import com.example.event.validation.Phone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Date;
@Data
public class UpdateUserReq {
    @NotBlank(message = "Email không được trống")
    @Email(message = "Email không hợp lệ")
    private String email;
    @NotBlank(message = "Tên không được trống")
    private String name;
    @Phone
    private String phone;
    private String fileId;
    private String address;
    private Date dob;
    private String roleId;
}
