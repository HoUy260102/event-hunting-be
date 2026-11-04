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
public class AuthForgotPasswordConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final MailService mailService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String IDEMPOTENCY_KEY_FORMAT = "idempotency:email:auth-forgot:%s:%s";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SENT = "SENT";

    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofMinutes(15);

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String recordId = record.getId().getValue();
        log.info("[AUTH-FORGOT-CONSUMER] Bắt đầu xử lý gửi email OTP record={}", recordId);

        String idempotencyKey = null;
        
        try {
            String email = record.getValue().get("email");
            String otp = record.getValue().get("otp");

            if (email == null || otp == null) {
                log.warn("[AUTH-FORGOT-CONSUMER] Thiếu trường dữ liệu trong record {}, tiến hành ACK.", recordId);
                ack(record);
                return;
            }

            idempotencyKey = String.format(IDEMPOTENCY_KEY_FORMAT, email, otp);

            // Kiểm tra Idempotency bằng atomic SETNX
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, STATUS_PROCESSING, LOCK_TIMEOUT);

            if (Boolean.FALSE.equals(acquired)) {
                String currentStatus = stringRedisTemplate.opsForValue().get(idempotencyKey);
                if (STATUS_SENT.equals(currentStatus)) {
                    log.info("[AUTH-FORGOT-CONSUMER] Email OTP cho email={} với OTP={} đã được gửi thành công trước đó. Bỏ qua và ACK record={}",
                            email, otp, recordId);
                    ack(record);
                    return;
                } else if (STATUS_PROCESSING.equals(currentStatus)) {
                    log.warn("[AUTH-FORGOT-CONSUMER] Email OTP cho email={} đang được worker khác xử lý. Bỏ qua record={}",
                            email, recordId);
                    return;
                }
            }

            // Gửi mail OTP đặt lại mật khẩu
            mailService.sendForgotPasswordOtp(email, otp);

            // Đánh dấu đã gửi thành công
            stringRedisTemplate.opsForValue().set(idempotencyKey, STATUS_SENT, COMPLETED_TTL);

            // ACK sau khi gửi thành công
            ack(record);
            log.info("[AUTH-FORGOT-CONSUMER] Gửi OTP thành công record={} cho email={}", recordId, email);

        } catch (Exception e) {
            log.error("[AUTH-FORGOT-CONSUMER] Gửi OTP thất bại record={}: {}", recordId, e.getMessage(), e);
            if (idempotencyKey != null) {
                try {
                    String status = stringRedisTemplate.opsForValue().get(idempotencyKey);
                    if (STATUS_PROCESSING.equals(status)) {
                        stringRedisTemplate.delete(idempotencyKey);
                    }
                } catch (Exception ex) {
                    log.error("[AUTH-FORGOT-CONSUMER] Lỗi khi giải phóng lock idempotency cho key={}: {}", idempotencyKey, ex.getMessage());
                }
            }
        }
    }

    private void ack(MapRecord<String, String, String> record) {
        stringRedisTemplate.opsForStream().acknowledge(
                RedisStreamConfig.FORGOT_PASSWORD_STREAM_KEY,
                RedisStreamConfig.FORGOT_PASSWORD_GROUP_NAME,
                record.getId());
    }
}
