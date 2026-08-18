package com.example.event.projection;

import java.time.LocalDateTime;

public interface TopShowProjection {
    String getShowId();
    LocalDateTime getStartTime();
    Long getTicketsSold();
    Long getRevenue();
}
