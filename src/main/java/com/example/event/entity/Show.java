package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import com.example.event.constant.SeatMapType;
import com.example.event.constant.ShowStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "event_show")
public class Show {
    @Id
    @UlidID
    private String id;
    private Integer minOrder;
    private Integer maxOrder;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @Enumerated(EnumType.STRING)
    private SeatMapType seatMapType;
    @Column(columnDefinition = "TEXT")
    private String seatMapSvg;
    @Enumerated(EnumType.STRING)
    private ShowStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @OneToMany(mappedBy = "show")
    @Fetch(FetchMode.SUBSELECT)
    private List<TicketType> ticketTypes;

    private Boolean isCleanedUp = false;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
