package com.example.event.service;

public interface CaptchaService {
    /**
     * Xác thực token captcha nhận từ client.
     *
     * @param token    Token captcha sinh ra từ Turnstile
     * @param clientIp Địa chỉ IP của client (tùy chọn)
     * @return true nếu hợp lệ hoặc nếu captcha đang bị tắt, ngược lại false
     */
    boolean verifyToken(String token, String clientIp);
}

