package com.example.event.dto;

import com.example.event.constant.ShowStatus;
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
public class ShowSummaryDTO {
//    private String id;
//    private String startDay;
//    private String startMonth;
//
//    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
//    private LocalDateTime startTime;
//    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
//    private LocalDateTime endTime;
//
//    private ShowStatus status;
//
//    private Integer totalQuantity;
//    private Integer soldQuantity;
//    private Long totalRevenue;
//    private List<TicketTypeSummaryDTO> ticketTypes;

    private String id;
    private String startDay;
    private String startMonth;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;

    private ShowStatus status;

    private Integer totalQuantity;
    private Integer soldQuantity;

    private Long totalAmount;
    private Long discountAmount;
    private Long finalAmount;

    private List<TicketTypeSummaryDTO> ticketTypes;
}
