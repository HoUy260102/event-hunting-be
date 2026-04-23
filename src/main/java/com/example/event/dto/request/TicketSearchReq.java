package com.example.event.dto.request;

import lombok.Data;

@Data
public class TicketSearchReq {
    private String keyword;
    private String status = "ALL";
    private Integer size = 5;
    private Integer page = 1;
    private String showId;
}
