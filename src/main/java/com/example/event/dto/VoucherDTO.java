package com.example.event.dto;
import com.example.event.constant.DiscountType;
import com.example.event.constant.VoucherScope;
import com.example.event.constant.VoucherStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VoucherDTO {

    private String id;

    private String name;
    private String code;

    private Integer quantity;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;

    private VoucherStatus status;
    private Long discountValue;
    private DiscountType discountType;

    private Long minOrderValue;
    private Long maxDiscountValue;

    private VoucherScope scope;

    // Organizer
    private String showId;
    private List<String> ticketTypeIds;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
