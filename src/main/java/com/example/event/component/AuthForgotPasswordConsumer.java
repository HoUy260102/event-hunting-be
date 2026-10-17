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
public class AuthForgotPasswordConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final MailService mailService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String recordId = record.getId().getValue();
        log.info("[AUTH-FORGOT-CONSUMER] Bắt đầu xử lý gửi email OTP record={}", recordId);

        try {
            String email = record.getValue().get("email");
            String otp = record.getValue().get("otp");

            if (email == null || otp == null) {
                log.warn("[AUTH-FORGOT-CONSUMER] Thiếu trường dữ liệu trong record {}, tiến hành ACK.", recordId);
                ack(record);
                return;
            }

            // Gửi mail OTP đặt lại mật khẩu
            mailService.sendForgotPasswordOtp(email, otp);

            // ACK sau khi gửi thành công
            ack(record);
            log.info("[AUTH-FORGOT-CONSUMER] Gửi OTP thành công record={} cho email={}", recordId, email);

        } catch (Exception e) {
            log.error("[AUTH-FORGOT-CONSUMER] Gửi OTP thất bại record={}: {}", recordId, e.getMessage(), e);
        }
    }

    private void ack(MapRecord<String, String, String> record) {
        stringRedisTemplate.opsForStream().acknowledge(
                RedisStreamConfig.FORGOT_PASSWORD_STREAM_KEY,
                RedisStreamConfig.FORGOT_PASSWORD_GROUP_NAME,
                record.getId());
    }
}
