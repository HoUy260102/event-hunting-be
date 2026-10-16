package com.example.event.component;

import com.example.event.config.RedisStreamConfig;
import com.example.event.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthForgotPasswordProducer {

    private final OutboxService outboxService;

    public void sendForgotPasswordOtpEmail(String email, String otp) {
        try {
            Map<String, String> fields = Map.of(
                    "email", email,
                    "otp", otp,
                    "type", "AUTH_FORGOT_PASSWORD_OTP"
            );

            outboxService.publish("USER", email, "AUTH_FORGOT_PASSWORD_OTP", RedisStreamConfig.FORGOT_PASSWORD_STREAM_KEY, fields);
            log.info("[AUTH-FORGOT-PRODUCER] Queued OTP email qua Outbox (email={})", email);
        } catch (Exception e) {
            log.error("[AUTH-FORGOT-PRODUCER] Lỗi lưu OTP email vào Outbox: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi lưu sự kiện gửi mail OTP vào Outbox", e);
        }
    }
}
