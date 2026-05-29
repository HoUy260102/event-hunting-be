package com.example.event.dto.request;

import com.example.event.constant.EventStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class EventSearchPublicReq {
    private String keyword;
    private String nextId;
    private Integer size = 8;
    private Long minPrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String provinceId;
    private List<String> categoryIds;
    private List<EventStatus> statuses;
}


