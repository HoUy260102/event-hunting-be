package com.example.event.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SearchReservationReq {
    private String keyword = "";
    @Pattern(
            regexp = "^(ALL|PENDING|EXPIRED|CANCELLED|PAID)$",
            message = "Trạng thái phải là một trong các giá trị: ALL, PENDING, EXPIRED, CANCELLED, PAID"
    )
    private String status = "ALL";
    private String eventId = "";
    private String showId = "";
    private Integer page = 1;
    private Integer size = 5;
}
