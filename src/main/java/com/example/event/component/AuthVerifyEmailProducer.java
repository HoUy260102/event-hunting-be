package com.example.event.component;

import com.example.event.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthVerifyEmailProducer {

    private final com.example.event.service.OutboxService outboxService;

    public void sendVerifyEmail(String email, String verifyToken) {
        try {
            Map<String, String> fields = Map.of(
                    "email", email,
                    "verifyToken", verifyToken
            );

            outboxService.publish("USER", email, "AUTH_VERIFY_EMAIL", RedisStreamConfig.AUTH_STREAM_KEY, fields);
            log.info("[AUTH-PRODUCER] Queued verify email qua Outbox (email={})", email);
        } catch (Exception e) {
            log.error("[AUTH-PRODUCER] Lỗi lưu verify email vào Outbox: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi lưu sự kiện gửi mail xác thực vào Outbox", e);
        }
    }
}
