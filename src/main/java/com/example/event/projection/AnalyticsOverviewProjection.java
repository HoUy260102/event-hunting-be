package com.example.event.projection;

public interface AnalyticsOverviewProjection {
    Long getTotalRevenue();
    Long getTotalTicketsSold();
    Long getTotalBookings();
    Long getTotalEventsCreated();
}
