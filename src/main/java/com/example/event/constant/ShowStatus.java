package com.example.event.constant;

import lombok.Getter;

@Getter
public enum ShowStatus {
    //Trạng thái lưu db
    DRAFT("Bản nháp"),
    ACTIVE("Hoạt động"),
    POSTPONED("Tạm hoãn"),
    CANCELLED("Đã hủy"),

    //Trạng thái để hiển thị
    SOLD_OUT("Hết vé"),
    ON_SALE("Đang mở bán"),
    UPCOMING("Sắp diễn ra"),
    HAPPENING("Đang diễn ra"),
    FINISHED("Đã kết thúc"),
    DELETED("Đã xóa");

    private final String displayName;

    ShowStatus(String displayName) {
        this.displayName = displayName;
    }
}
