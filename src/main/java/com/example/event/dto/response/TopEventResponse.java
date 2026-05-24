package com.example.event.dto.response;

import lombok.*;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopEventResponse {
    private String id;
    private String name;
    private Long revenue;
    private Long ticketsSold;
    private List<TopShowResponse> topShows;
}
