package com.example.event.dto;

import com.example.event.constant.SeatingType;
import com.example.event.constant.TicketTypeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketTypeDTO {
    private String id;
    private String name;
    private Integer totalQuantity;
    private Integer soldQuantity;
    private TicketTypeStatus status;
    private SeatingType seatingType;
    private String sectionId;
    private String seatMapSvg;
    private Long queueNo;

    private List<TicketTierDTO> ticketTiers;
    private List<SeatDTO> seats;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
