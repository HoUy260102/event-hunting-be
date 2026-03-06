package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import com.example.event.constant.TicketTierStatus;
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
public class TicketTier {
    @Id
    @UlidID
    private String id;
    private String name;
    private Long price;
    private Integer limitQuantity;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private String description;
    private Integer reservedQuantity = 0;
    private Integer soldQuantity = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;
    @Enumerated(EnumType.STRING)
    private TicketTierStatus status;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
