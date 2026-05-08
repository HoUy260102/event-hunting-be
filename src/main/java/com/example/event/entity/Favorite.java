package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "favorite", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "event_id"})
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Favorite {
    @Id
    @UlidID
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters và Setters
}
