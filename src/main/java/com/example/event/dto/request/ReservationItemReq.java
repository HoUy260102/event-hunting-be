package com.example.event.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ReservationItemReq {
    private String ticketTypeId;
    private String ticketTierId;
    private List<String> seatIds;

    private String ticketTypeName;
    private String ticketTierName;
    private List<String> seatCodes;

    private Long unitPrice;
    private Integer quantity;
    private Long totalPrice;
}
