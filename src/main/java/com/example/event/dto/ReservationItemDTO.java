package com.example.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReservationItemDTO {
    private String id;

    private String ticketTypeId;
    private String ticketTypeName;
    private String ticketTierId;
    private String ticketTierName;
    private String seatId;
    private String seatCode;
    private String seatDisplayName;

    private Long unitPrice;
    private Integer quantity;
    private Long totalPrice;
    private Long discountAmount = 0L;
    private Long finalPrice;
}
