package com.example.event.constant;

import lombok.Getter;

@Getter
public enum EventStatus {
    //Trạng thái lưu trong db
    DRAFT("Nháp"),
    PENDING("Chờ duyệt"),
    PUBLISHED("Đã công khai"),
    CANCELLED("Đã hủy"),
    REJECTED("Bị từ chối"),

    //Trạng thái để hiển thị
    UPCOMING("Sắp mở bán"),
    ON_SALE("Đang mở bán"),
    SOLD_OUT("Hết vé"),
    EXPIRED("Hết hạn bán vé"),
    HAPPENING("Đang diễn ra"),
    FINISHED("Đã kết thúc"),
    DELETED("Đã xóa");

    private final String displayName;

    EventStatus(String displayName) {
        this.displayName = displayName;
    }
}
