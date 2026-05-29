package com.example.event.dto;

import com.example.event.constant.ReservationStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReservationDetailDTO {
    private String id;
    private String code;

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

    private List<ReservationItemDTO> items = new ArrayList<>();

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime createdAt;
}
