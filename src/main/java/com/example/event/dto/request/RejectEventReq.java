package com.example.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectEventReq {
    @NotBlank(message = "Lý do từ chối không được để trống")
    private String rejectionReason;
}
