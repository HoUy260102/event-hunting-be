package com.example.event.dto;

import com.example.event.constant.TicketStatus;
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
public class TicketDetailDTO {
    private String id;
    private Long unitPrice;
    private String section;
    private String seatLabel;
    private String displayName;
    private String qrCode;

    private TicketStatus status;
    private LocalDateTime checkinAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime showStartTime;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime showEndTime;

    private String eventName;
    private String eventLocation;
    private FileDTO eventPoster;

    private String reservationId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
}
