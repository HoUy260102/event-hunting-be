package com.example.event.dto.response;

import lombok.*;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Setter
@Getter
public class AnalyticsOverviewResponse {
    private Long totalRevenue;
    private Long totalTicketsSold;
    private Long totalBookings;
    private Long totalEventsCreated;
    
    private List<TopEventResponse> topEvents;
}
