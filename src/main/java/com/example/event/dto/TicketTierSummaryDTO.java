package com.example.event.dto;

import com.example.event.constant.TicketTierStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketTierSummaryDTO {

    private String id;
    private String name;
    private Long unitPrice;

    private Integer limitQuantity;
    private Integer soldQuantity;
    private Integer reservedQuantity;

    private Long totalPrice;
    private Long discountAmount;
    private Long finalPrice;

    private TicketTierStatus adminStatus;
    private TicketTierStatus businessStatus;
}
