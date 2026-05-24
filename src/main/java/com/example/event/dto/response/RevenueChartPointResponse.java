package com.example.event.dto.response;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Setter
@Getter
public class RevenueChartPointResponse {
    private String label;
    private Long revenue;
    private Long ticketsSold;
}
