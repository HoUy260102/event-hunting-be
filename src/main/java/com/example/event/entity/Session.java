package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import com.example.event.constant.SessionRevokeReason;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Session {
    @Id
    @UlidID
    private String id;

    private String tokenFamily;
    private LocalDateTime tokenFamilyExpiresAt;
    @Column(name = "refresh_token_hash", length = 64, unique = true)
    private String refreshTokenHash;
    private LocalDateTime createdAt;
    private LocalDateTime expiryDate;
    private Integer tokenVersion;

    private boolean revoked = false;
    private LocalDateTime revokedAt;
    @Enumerated(EnumType.STRING)
    private SessionRevokeReason revokeReason;

    private String deviceId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
