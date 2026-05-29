package com.example.event.component;

import com.example.event.config.RedisStreamConfig;
import com.example.event.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthVerifyEmailConsumer
        implements StreamListener<String, MapRecord<String, String, String>> {

    private final MailService mailService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String recordId = record.getId().getValue();
        log.info("[AUTH-CONSUMER] Processing verify email record={}", recordId);

        try {
            String email = record.getValue().get("email");
            String verifyToken = record.getValue().get("verifyToken");

            if (email == null || verifyToken == null) {
                log.warn("[AUTH-CONSUMER] Missing fields in record {}, ACKing.", recordId);
                ack(record);
                return;
            }

            // Gửi mail xác thực
            mailService.registerUser(verifyToken, email);

            // ACK sau khi gửi thành công
            ack(record);
            log.info("[AUTH-CONSUMER] Done record={} email={}", recordId, email);

        } catch (Exception e) {
            log.error("[AUTH-CONSUMER] FAILED record={}: {}", recordId, e.getMessage(), e);
        }
    }

    private void ack(MapRecord<String, String, String> record) {
        stringRedisTemplate.opsForStream().acknowledge(
                RedisStreamConfig.AUTH_STREAM_KEY,
                RedisStreamConfig.AUTH_GROUP_NAME,
                record.getId()
        );
    }
}
