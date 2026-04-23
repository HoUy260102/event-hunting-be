package com.example.event.dto.request;

import com.example.event.constant.DiscountType;
import com.example.event.constant.VoucherScope;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateVoucherReq {

    @NotBlank(message = "Tên voucher không được để trống")
    private String name;

    @NotBlank(message = "Mã voucher không được để trống")
    @Size(min = 3, message = "Mã voucher phải ít nhất 3 ký tự")
    private String code;

    @NotNull(message = "Số lượng không được null")
    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private Integer quantity;

    @NotNull(message = "Ngày bắt đầu không được null")
    private LocalDateTime startTime;

    @NotNull(message = "Ngày kết thúc không được null")
    private LocalDateTime endTime;

    @NotNull(message = "Giá trị giảm không được null")
    @Min(value = 1, message = "Giá trị giảm phải > 0")
    private Long discountValue;

    @NotNull(message = "Giá trị tối thiểu không được null")
    @Min(value = 0, message = "Không được âm")
    private Long minOrderValue;

    private List<String> ticketTypeIds;

    private String showId;

    @NotNull(message = "Loại giảm giá không được null")
    private DiscountType discountType;

    @NotNull(message = "Scope không được null")
    private VoucherScope scope;

    private Long maxDiscountValue;
}