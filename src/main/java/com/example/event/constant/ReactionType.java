package com.example.event.constant;

import lombok.Getter;

@Getter
public enum ReactionType {
    LIKE("👍", "Thích"),
    LOVE("❤️", "Yêu thích"),
    HAHA("😆", "Haha"),
    WOW("😮", "Wow"),
    SAD("😢", "Buồn"),
    ANGRY("😡", "Phẫn nộ");

    private final String emoji;
    private final String displayName;

    ReactionType(String emoji, String displayName) {
        this.emoji = emoji;
        this.displayName = displayName;
    }
}
