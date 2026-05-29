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

    private final StringRedisTemplate stringRedisTemplate;

    public void sendVerifyEmail(String email, String verifyToken) {
        try {
            Map<String, String> fields = Map.of(
                    "email", email,
                    "verifyToken", verifyToken
            );

            RecordId id = stringRedisTemplate.opsForStream()
                    .add(RedisStreamConfig.AUTH_STREAM_KEY, fields);
            log.info("[AUTH-PRODUCER] Pushed verify email to stream (email={}) → record={}", email, id);
        } catch (Exception e) {
            log.error("[AUTH-PRODUCER] Lỗi push stream: {}", e.getMessage(), e);
        }
    }
}
