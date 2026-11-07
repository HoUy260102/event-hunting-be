package com.example.event.service.Impl;

import com.example.event.dto.response.TurnstileVerifyResponse;
import com.example.event.service.CaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Service
@Slf4j
public class TurnstileServiceImpl implements CaptchaService {

    @Value("${app.captcha.enabled:false}")
    private boolean captchaEnabled;

    @Value("${app.captcha.secret-key:}")
    private String secretKey;

    @Value("${app.captcha.bypass-token:DEV_BYPASS_TOKEN}")
    private String bypassToken;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestClient restClient;

    public TurnstileServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean verifyToken(String token, String clientIp) {
        // 1. Nếu cờ tắt -> Cho qua ngay lập tức để test API thuận tiện
        if (!captchaEnabled) {
            log.info("[TURNSTILE] Bỏ qua kiểm tra captcha do app.captcha.enabled = false");
            return true;
        }

        // 2. Kiểm tra bypass token cho môi trường test/dev (chỉ cho phép khi KHÔNG PHẢI là prod)
        if (!"prod".equalsIgnoreCase(activeProfile) && StringUtils.hasText(bypassToken) && bypassToken.equals(token)) {
            log.warn("[TURNSTILE] Bỏ qua kiểm tra captcha do sử dụng Bypass Token trên môi trường {}", activeProfile);
            return true;
        }

        // 3. Nếu token rỗng -> Không hợp lệ
        if (!StringUtils.hasText(token)) {
            log.warn("[TURNSTILE] Token rỗng hoặc null khi captcha đang bật");
            return false;
        }

        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", secretKey);
            body.add("response", token);
            if (StringUtils.hasText(clientIp)) {
                body.add("remoteip", clientIp);
            }

            TurnstileVerifyResponse response = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(TurnstileVerifyResponse.class);

            if (response != null && response.isSuccess()) {
                log.info("[TURNSTILE] Xác thực captcha thành công cho hostname: {}", response.getHostname());
                return true;
            } else {
                log.warn("[TURNSTILE] Xác thực thất bại, error codes: {}",
                        response != null ? response.getErrorCodes() : "null");
                return false;
            }
        } catch (Exception e) {
            log.error("[TURNSTILE] Lỗi kết nối khi xác thực với Cloudflare: {}", e.getMessage());
            return false;
        }
    }
}

