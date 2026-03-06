package com.example.event.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EventSearchReq {
    private String keyword = "";
    @Pattern(
            regexp = "^(ALL|DRAFT|PUBLISHED|CANCELLED|REJECTED|UPCOMING|ON_SALE|SOLD_OUT|EXPIRED|HAPPENING|FINISHED|DELETED)$",
            message = "Trạng thái phải là một trong các giá trị: ALL|DRAFT|PUBLISHED|CANCELLED|REJECTED|UPCOMING|ON_SALE|SOLD_OUT|EXPIRED|HAPPENING|FINISHED|DELETED"
    )
    private String status = "ALL";
    private String provinceId = "";
    private String categoryId = "";
    private Integer page = 1;
    private Integer size = 5;
}
