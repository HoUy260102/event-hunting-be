package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import com.example.event.constant.AssignmentType;
import com.example.event.constant.SeatStatus;
import com.example.event.constant.SeatingType;
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
public class Seat {
    @Id
    @UlidID
    private String id;
    private Long queueNo;
    private String rowName;
    private String seatNumber;
    private String seatCode;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;
    @Enumerated(EnumType.STRING)
    private AssignmentType assignmentType;
    @Enumerated(EnumType.STRING)
    private SeatingType seatingType;

    private LocalDateTime holdExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserved_by_id")
    private User reservedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
