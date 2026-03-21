package com.example.event.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ReservationReq {
    private String showId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    private Long totalAmount;
    private Long finalAmount;

    private List<ReservationItemReq> items;
}
