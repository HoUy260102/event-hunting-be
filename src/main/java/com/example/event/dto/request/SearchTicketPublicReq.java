package com.example.event.dto.request;

import lombok.Data;

@Data
public class SearchTicketPublicReq {
    private Integer size = 8;
    private Integer pageNumber = 1;
    private Boolean isFinished = false;
}
