package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import com.example.event.constant.SeatingType;
import com.example.event.constant.TicketTypeStatus;
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
public class TicketType {
    @Id
    @UlidID
    private String id;
    private String name;
    private Integer totalQuantity;
    private Integer reservedQuantity = 0;
    private Integer soldQuantity = 0;
    @Enumerated(EnumType.STRING)
    private TicketTypeStatus status;
    @Enumerated(EnumType.STRING)
    private SeatingType seatingType;
    private String sectionId;
    private Long queueNo = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @OneToMany(mappedBy = "ticketType")
    @Fetch(FetchMode.SUBSELECT)
    private List<TicketTier> ticketTiers;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
