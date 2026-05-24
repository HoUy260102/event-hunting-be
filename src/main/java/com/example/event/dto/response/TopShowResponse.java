package com.example.event.dto.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopShowResponse {
    private String showId;
    private String startTime;
    private Long ticketsSold;
    private Long revenue;
}
