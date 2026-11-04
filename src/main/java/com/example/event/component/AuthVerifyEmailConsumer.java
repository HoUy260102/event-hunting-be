package com.example.event.component;

import com.example.event.config.RedisStreamConfig;
import com.example.event.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthVerifyEmailConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final MailService mailService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String IDEMPOTENCY_KEY_FORMAT = "idempotency:email:auth-verify:%s";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SENT = "SENT";

    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String recordId = record.getId().getValue();
        log.info("[AUTH-CONSUMER] Bắt đầu xử lý gửi email xác thực record={}", recordId);

        String idempotencyKey = null;
        try {
            String email = record.getValue().get("email");
            String verifyToken = record.getValue().get("verifyToken");

            if (email == null || verifyToken == null) {
                log.warn("[AUTH-CONSUMER] Thiếu email hoặc verifyToken trong record {}, tiến hành ACK.", recordId);
                ack(record);
                return;
            }

            idempotencyKey = String.format(IDEMPOTENCY_KEY_FORMAT, verifyToken);

            // Kiểm tra Idempotency bằng atomic SETNX
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, STATUS_PROCESSING, LOCK_TIMEOUT);

            if (Boolean.FALSE.equals(acquired)) {
                String currentStatus = stringRedisTemplate.opsForValue().get(idempotencyKey);
                if (STATUS_SENT.equals(currentStatus)) {
                    log.info("[AUTH-CONSUMER] Email xác thực cho token={} đã được gửi thành công trước đó. Bỏ qua và ACK record={}",
                            verifyToken, recordId);
                    ack(record);
                    return;
                } else if (STATUS_PROCESSING.equals(currentStatus)) {
                    log.warn("[AUTH-CONSUMER] Email xác thực cho token={} đang được worker khác xử lý. Bỏ qua record={}",
                            verifyToken, recordId);
                    return;
                }
            }

            // Gửi mail xác thực đăng ký tài khoản
            mailService.registerUser(verifyToken, email);

            // Đánh dấu đã gửi thành công
            stringRedisTemplate.opsForValue().set(idempotencyKey, STATUS_SENT, COMPLETED_TTL);

            // ACK sau khi gửi thành công
            ack(record);
            log.info("[AUTH-CONSUMER] Gửi email xác thực thành công record={} cho email={}", recordId, email);

        } catch (Exception e) {
            log.error("[AUTH-CONSUMER] Gửi email xác thực thất bại record={}: {}", recordId, e.getMessage(), e);
            if (idempotencyKey != null) {
                try {
                    String status = stringRedisTemplate.opsForValue().get(idempotencyKey);
                    if (STATUS_PROCESSING.equals(status)) {
                        stringRedisTemplate.delete(idempotencyKey);
                    }
                } catch (Exception ex) {
                    log.error("[AUTH-CONSUMER] Lỗi khi giải phóng lock idempotency cho key={}: {}", idempotencyKey, ex.getMessage());
                }
            }
        }
    }

    private void ack(MapRecord<String, String, String> record) {
        stringRedisTemplate.opsForStream().acknowledge(
                RedisStreamConfig.AUTH_STREAM_KEY,
                RedisStreamConfig.AUTH_GROUP_NAME,
                record.getId());
    }
}
