package com.example.event.dto.request;

import com.example.event.constant.SeatingType;
import com.example.event.constant.TicketTypeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateTicketTypeReq {
    private String id;
    private TicketTypeStatus status;

    @NotBlank(message = "Tên vé không được để trống")
    private String name;

    @NotNull(message = "Số lượng tối thiểu là 1")
    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    private Integer totalQuantity;

    @NotNull(message = "Vui lòng chọn hình thức chỗ ngồi")
    private SeatingType seatingType;
    private String sectionId;

    @Valid
    @NotEmpty(message = "Phải có ít nhất một đợt mở bán (Tier)")
    private List<UpdateTicketTierReq> ticketTiers;
}
