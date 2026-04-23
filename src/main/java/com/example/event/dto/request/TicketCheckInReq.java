package com.example.event.dto.request;

import com.example.event.constant.CheckInMethod;
import lombok.Data;

@Data
public class TicketCheckInReq {
    private CheckInMethod checkInMethod;
    private String showId;
}
