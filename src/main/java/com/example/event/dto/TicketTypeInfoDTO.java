package com.example.event.dto;

import com.example.event.constant.SeatingType;
import com.example.event.constant.TicketTierStatus;
import com.example.event.constant.TicketTypeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketTypeInfoDTO {
    private String id;
    private String name;
    private TicketTypeStatus status;
    private SeatingType seatingType;
    private String tierId;
    private String tierName;
    private Long tierPrice;
    private TicketTierStatus tierStatus;
    private String tierDescription;
}
