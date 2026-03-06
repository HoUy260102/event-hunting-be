package com.example.event.dto.request;

import com.example.event.constant.ShowStatus;
import lombok.Data;

@Data
public class UpdateShowStatusReq {
    private ShowStatus status;
}
