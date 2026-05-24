package com.example.event.dto.response;

import com.example.event.projection.EventTrendingProjection;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventTrendingResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String location;
    private String posterUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double trendingScore;

    public static EventTrendingResponse fromProjection(EventTrendingProjection projection) {
        if (projection == null) return null;
        return EventTrendingResponse.builder()
                .id(projection.getId())
                .name(projection.getName())
                .location(projection.getLocation())
                .posterUrl(projection.getPosterUrl())
                .startTime(projection.getStartTime())
                .endTime(projection.getEndTime())
                .trendingScore(projection.getTrendingScore())
                .build();
    }
}
