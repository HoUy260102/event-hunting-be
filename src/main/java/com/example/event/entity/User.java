package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class User {
    @Id
    @UlidID
    private String id;
    private String email;
    private String password;
    private String name;
    private String phone;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "avatar_id", unique = true)
    private File avatar;

    private String address;
    private Date dob;
    
//    private String organizerDescription;
//    private String organizerEmail;
//    private String organizerWebsiteUrl;
//    private String organizerContactName;

    // Trạng thái khóa tài khoản
    private LocalDateTime lockAt;
    private LocalDateTime lockUtil;
    private String lockMessage;
//    private LockReason lockReason;

    // Xác minh tài khoản
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    // Sự kiện quan tâm

    // Vai trò
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    // Liên kết hệ thống ngoài
    private String provider;

    // Thông tin meta
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
