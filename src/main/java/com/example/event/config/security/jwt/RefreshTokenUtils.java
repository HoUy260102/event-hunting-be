package com.example.event.config.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Component
public class RefreshTokenUtils {
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public String generateToken() {
        return UUID.randomUUID().toString();
    }

    public String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Refresh token không được để trống.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không hỗ trợ thuật toán SHA-256.", e);
        }
    }

    public LocalDateTime getInitialExpiry(LocalDateTime createdAt) {
        return createdAt.plus(Duration.ofMillis(refreshExpiration));
    }

    public LocalDateTime getRotatedExpiry(LocalDateTime now, LocalDateTime familyExpiresAt) {
        LocalDateTime slidingExpiry = getInitialExpiry(now);
        return slidingExpiry.isBefore(familyExpiresAt) ? slidingExpiry : familyExpiresAt;
    }
}