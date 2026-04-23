package com.example.event.dto;

import com.example.event.constant.EventStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventSummaryDTO {

    private String id;
    private String name;
    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;

    private String posterUrl;
    private EventStatus status;

    private Long totalAmount;
    private Long discountAmount;
    private Long totalFinalAmount;

    private Integer totalQuantity;
    private Integer soldQuantity;

    private List<ShowSummaryDTO> shows;
}
