package com.example.event.dto.request;

import com.example.event.constant.EventStatus;
import lombok.Data;

@Data
public class UpdateEventStatusReq {
    private EventStatus status;
}
