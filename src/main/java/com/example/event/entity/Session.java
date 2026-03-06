package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
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
    private String deviceId;
    private String refreshToken;
    private LocalDateTime createdAt;
    private LocalDateTime expiryDate;
    private boolean revoked = false;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
