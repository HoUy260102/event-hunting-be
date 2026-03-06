package com.example.event.constant;

import lombok.Getter;

@Getter
public enum TicketTypeStatus {
    ACTIVE("Đang hoạt động"),
    SUSPENDED("Ngưng bán"),
    INACTIVE("Ngưng hoạt động"),

    COMING_SOON("Sắp mở bán"),
    ON_SALE("Đang mở bán"),
    SOLD_OUT("Hết vé"),
    TIER_SOLD_OUT("Hết vé đợt hiện tại"),
    EXPIRED("Hết hạn"),
    DELETED("Đã xóa");

    private final String displayName;

    TicketTypeStatus(String displayName) {
        this.displayName = displayName;
    }
}
