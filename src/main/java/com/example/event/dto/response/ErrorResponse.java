package com.example.event.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder
public class ErrorResponse<T>{
    private String code;            // Mã lỗi nội bộ (ví dụ: "REFRESH_EXPIRED")
    private T message;      // Thông báo ngắn gọn dành cho người dùng hoặc dev
    private Map<String, String> details;
    private int status;          // Mã HTTP status (ví dụ: 401)
    private LocalDateTime timestamp;
}
