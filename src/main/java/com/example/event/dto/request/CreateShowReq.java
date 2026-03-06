package com.example.event.dto.request;

import com.example.event.constant.SeatMapType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateShowReq {
    @NotNull(message = "Vui lòng nhập số")
    @Min(value = 1, message = "Số lượng tối thiểu mỗi đơn phải lớn hơn hoặc bằng 1")
    private Integer minOrder;

    @NotNull(message = "Vui lòng nhập số")
    @Min(value = 1, message = "Số lượng tối đa mỗi đơn phải lớn hơn hoặc bằng 1")
    private Integer maxOrder;

    @NotNull(message = "Vui lòng chọn thời gian bắt đầu")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;

    @NotNull(message = "Vui lòng chọn thời gian kết thúc")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;

    @NotNull(message = "Vui lòng chọn sơ đồ ghế")
    private SeatMapType seatMapType;
    private String seatMapSvg;

    @Valid
    @NotEmpty(message = "Phải có ít nhất 1 loại vé cho suất diễn này")
    private List<CreateTicketTypeReq> ticketTypes;
}
