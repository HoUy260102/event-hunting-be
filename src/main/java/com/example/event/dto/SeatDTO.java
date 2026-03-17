package com.example.event.dto;

import com.example.event.constant.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SeatDTO {
    private String id;
    private String rowName;
    private String seatNumber;
    private String seatCode;
    private SeatStatus status;
}
