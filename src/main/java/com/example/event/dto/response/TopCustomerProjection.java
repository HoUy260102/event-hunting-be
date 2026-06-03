package com.example.event.dto.response;

public interface TopCustomerProjection {
    String getUserId();
    String getName();
    String getEmail();
    String getAvatarUrl();
    Long getTotalBookings();
    Long getTotalSpent();
    Long getTotalTickets();
}
