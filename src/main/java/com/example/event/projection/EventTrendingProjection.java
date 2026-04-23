package com.example.event.projection;

import java.time.LocalDateTime;

public interface EventTrendingProjection {

    String getId();

    String getName();

    String getLocation();

    String getPosterUrl();

    LocalDateTime getStartTime();

    LocalDateTime getEndTime();

    Double getTrendingScore();
}
