package com.example.event.projection;

public interface TopCustomerProjection {
    String getUserId();
    String getName();
    String getEmail();
    String getAvatarUrl();
    Long getTotalBookings();
    Long getTotalSpent();
    Long getTotalTickets();
}
