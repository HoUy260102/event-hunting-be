package com.example.event.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SearchVoucherReq {
    private String keyword = "";
    @Pattern(
            regexp = "^(ALL|ACTIVE|INACTIVE|DRAFT)$",
            message = "Trạng thái phải là một trong các giá trị: ALL, ACTIVE, INACTIVE, DELETED"
    )
    private String status = "ALL";
    private Integer page = 1;
    private Integer size = 5;
}
