package com.example.event.dto.request;

import lombok.Data;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class SearchTicketPublicReq {
    private Integer size = 8;
    private Integer pageNumber = 1;
    private Boolean isFinished = false;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}

