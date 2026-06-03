package com.example.event.mapper;

import com.example.event.constant.UserStatus;
import com.example.event.dto.UserDTO;
import com.example.event.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class UserMapper {
    private final RoleMapper roleMapper;
    private final FileMapper fileMapper;

    public UserDTO toDTO(User user) {
        if (user == null) return null;

        UserDTO.UserDTOBuilder builder = UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .dob(user.getDob())
                .lockAt(user.getLockAt())
                .lockUtil(user.getLockUtil())
                .lockMessage(user.getLockMessage())
                .status(user.getDeletedAt() != null ? UserStatus.DELETED : user.getStatus())
                .isVerified(user.isVerified())
                .verifiedAt(user.getVerifiedAt())
                .provider(user.getProvider())
                .createdAt(user.getCreatedAt())
                .createdBy(user.getCreatedBy())
                .updatedAt(user.getUpdatedAt())
                .updatedBy(user.getUpdatedBy())
                .deletedAt(user.getDeletedAt())
                .deletedBy(user.getDeletedBy());

        if (user.getLockUtil() != null) {
            builder.isLocked(user.getLockUtil().isAfter(LocalDateTime.now()));
        }

        // Map role safely
        try {
            if (user.getRole() != null) {
                builder.role(roleMapper.toDTO(user.getRole()));
            }
        } catch (Exception e) {
            // Bỏ qua nếu proxy role chưa được init và không có session
        }

        // Map avatar safely
        try {
            if (user.getAvatar() != null) {
                builder.avatar(fileMapper.toDTO(user.getAvatar()));
            }
        } catch (Exception e) {
            // Bỏ qua nếu proxy avatar chưa được init và không có session
        }

        return builder.build();
    }
}
