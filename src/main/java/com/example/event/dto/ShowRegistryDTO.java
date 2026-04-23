package com.example.event.dto;

import com.example.event.constant.ShowStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShowRegistryDTO {
    private String id;
    private String eventName;
    private String eventLocation;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;

    private ShowStatus status;

    private Integer totalTickets;
    private Integer checkedInCount;
    private Integer remainingCount;
}
