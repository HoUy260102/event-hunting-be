package com.example.event.dto;

import com.example.event.constant.CheckInMethod;
import com.example.event.constant.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketDTO {
    private String id;
    private Long unitPrice;
    private String section;
    private String seatLabel;
    private String displayName;
    private String qrCode;

    private TicketStatus status;
    private CheckInMethod checkInMethod;
    private LocalDateTime checkInAt;

    private String reservationId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
}
