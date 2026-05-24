package com.example.event.dto.response;

import java.time.LocalDateTime;

public interface TopShowProjection {
    String getShowId();
    LocalDateTime getStartTime();
    Long getTicketsSold();
    Long getRevenue();
}
