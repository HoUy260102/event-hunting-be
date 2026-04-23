package com.example.event.dto;

import com.example.event.constant.TicketTypeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketTypeSummaryDTO {

    private String id;
    private String name;

    private Integer totalQuantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Integer soldQuantity;

    private Long totalPrice;
    private Long discountAmount;
    private Long finalPrice;

    private TicketTypeStatus adminStatus;
    private TicketTypeStatus businessStatus;

    private List<TicketTierSummaryDTO> ticketTiers;
}
