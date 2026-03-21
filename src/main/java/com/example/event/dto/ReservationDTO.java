package com.example.event.dto;

import com.example.event.constant.ReservationStatus;
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
public class ReservationDTO {
    private String id;
    private String showId;
    private String eventId;

    private String userId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    private Long totalAmount;
    private Long finalAmount;

    private ReservationStatus status;
    private LocalDateTime expiresAt;
    private List<ReservationItemDTO> items = new ArrayList<>();
}
