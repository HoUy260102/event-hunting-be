package com.example.event.dto;

import com.example.event.constant.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDTO {
    private String id;
    private String email;
    private String name;
    private String phone;
    private String address;
    private Date dob;

    private FileDTO avatar;
    private UserStatus status;

    // Trạng thái khóa tài khoản
    private boolean isLocked;
    private LocalDateTime lockAt;
    private LocalDateTime lockUtil;
    private String lockMessage;

    // Xác minh tài khoản
    private boolean isVerified;
    private LocalDateTime verifiedAt;

    // Vai trò
    private RoleDTO role;

    // Liên kết hệ thống ngoài
    private String provider;

    // Thông tin meta data
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
