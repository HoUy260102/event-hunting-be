package com.example.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSeatReq {
    @NotBlank(message = "Tên rowName không được để trống")
    private String rowName;
    @NotBlank(message = "Tên seatNumber không được để trống")
    private String seatNumber;
    @NotBlank(message = "Tên seatCode không được để trống")
    private String seatCode;
}
