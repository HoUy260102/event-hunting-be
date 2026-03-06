package com.example.event.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateTicketTierReq {
    @NotBlank(message = "Tên tier không được để trống")
    private String name;

    @NotNull(message = "Giá không được để trống")
    @Min(value = 0, message = "Giá không được âm")
    private Long price;

    @NotNull(message = "Số lượng tối thiểu là 1")
    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    private Integer limitQuantity;

    @NotNull(message = "Vui lòng chọn thời gian bắt đầu")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime saleStartTime;

    @NotNull(message = "Vui lòng chọn thời gian kết thúc")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime saleEndTime;

    private String description;
}
