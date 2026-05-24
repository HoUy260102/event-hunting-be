package com.example.event.dto.response;

public interface AnalyticsOverviewProjection {
    Long getTotalRevenue();
    Long getTotalTicketsSold();
    Long getTotalBookings();
    Long getTotalEventsCreated();
}
