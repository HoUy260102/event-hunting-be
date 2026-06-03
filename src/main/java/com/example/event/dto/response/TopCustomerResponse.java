package com.example.event.dto.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopCustomerResponse {
    private String userId;
    private String name;
    private String email;
    private String avatarUrl;
    private Long totalBookings;
    private Long totalSpent;
    private Long totalTickets;
}
