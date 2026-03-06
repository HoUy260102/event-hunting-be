package com.example.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthDTO {
    private String id;
    private String email;
    private String password;
    private String name;
    private String phone;

    private String address;
    private String avatarUrl;
    private String avatarUrlId;

    // Thông tin riêng cho từng loại người dùng
    private LocalDateTime dob;

    // Trạng thái khóa tài khoản
    private boolean isLocked;
    private LocalDateTime lockAt;
    private LocalDateTime lockUtil;
    private String lockMessage;

    // Xác minh tài khoản
    private boolean isVerified;
    private LocalDateTime verifiedAt;

    // Sự kiện quan tâm

    // Vai trò
    private String roleId;
    private String role;
    // Quyền
    private List<String> permissions;
    // Liên kết hệ thống ngoài
    private String provider;
}
