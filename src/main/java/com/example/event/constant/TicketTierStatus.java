package com.example.event.constant;

import lombok.Getter;

@Getter
public enum TicketTierStatus {
    ACTIVE ("Đang hoạt động"),
    SUSPENDED("Ngưng bán"),
    INACTIVE("Ngưng hoạt động"),

    SOLD_OUT ("Hết vé"),
    COMING_SOON("Sắp mở bán"),
    ON_SALE("Đang mở bán"),
    EXPIRED("Hết hạn"),
    DELETED("Đã xóa");

    private final String displayName;
    TicketTierStatus(String displayName) {
        this.displayName = displayName;
    }
}
