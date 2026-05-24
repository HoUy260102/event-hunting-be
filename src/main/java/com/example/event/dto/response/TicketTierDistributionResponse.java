package com.example.event.dto.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketTierDistributionResponse {
    private String tierName;
    private Long quantity;
}
