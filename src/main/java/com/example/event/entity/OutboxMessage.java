package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import com.example.event.constant.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "outbox_messages",
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, createdAt"),
                @Index(name = "idx_outbox_status_processed", columnList = "status, processedAt")
        }
)
public class OutboxMessage {

    @Id
    @UlidID
    private String id;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false, length = 36)
    private String aggregateId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String destination;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Builder.Default
    @Column(nullable = false)
    private Integer retryCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer maxRetries = 5;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = OutboxStatus.PENDING;
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        if (this.maxRetries == null) {
            this.maxRetries = 5;
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
