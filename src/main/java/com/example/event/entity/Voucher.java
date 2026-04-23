package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import com.example.event.constant.DiscountType;
import com.example.event.constant.VoucherScope;
import com.example.event.constant.VoucherStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Voucher {

    @Id
    @UlidID
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoucherStatus status = VoucherStatus.DRAFT;

    @Column(nullable = false)
    private Long discountValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Column(nullable = false)
    private Long minOrderValue;

    private Long maxDiscountValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoucherScope scope;

    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    @Column(nullable = false)
    private Integer usedQuantity = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id")
    private Show show;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "voucher_ticket_type",
            joinColumns = @JoinColumn(name = "voucher_id"),
            inverseJoinColumns = @JoinColumn(name = "ticket_type_id")
    )
    private List<TicketType> ticketTypes;

    @Version
    private Long version = 0L;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}