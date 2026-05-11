package com.example.event.dto;

import com.example.event.constant.ReservationStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReservationViewDTO {
    private String id;

    private String showId;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime showStartTime;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime showEndTime;

    private String eventId;
    private String eventName;
    private String eventLocation;

    private String userId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    private ReservationStatus status;
    private Long totalAmount;
    private Long discountAmount;
    private Long finalAmount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime createdAt;
}
