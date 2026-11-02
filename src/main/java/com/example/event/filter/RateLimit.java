package com.example.event.filter;

import com.example.event.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RateLimit extends OncePerRequestFilter {
    private final RedisTemplate<String, Object> redisTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String LUA_SCRIPT = "local key = KEYS[1] " +
            "local member = ARGV[1] " +
            "local score = tonumber(ARGV[2]) " +
            "local minScore = tonumber(ARGV[3]) " +
            "local limit = tonumber(ARGV[4]) " +
            "local ttl = tonumber(ARGV[5]) " +
            "redis.call('ZADD', key, score, member) " +
            "redis.call('ZREMRANGEBYSCORE', key, 0, minScore) " +
            "local count = redis.call('ZCARD', key) " +
            "redis.call('EXPIRE', key, ttl) " +
            "if count > limit then return 0 else return 1 end";
    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    @Getter
    @AllArgsConstructor
    private static class RateLimitConfig {
        private final long maxRequests;
        private final long windowSec;
    }

    // Cầu chì tổng: 300 requests / 60 giây trên toàn bộ IP
    private static final RateLimitConfig GLOBAL_LIMIT = new RateLimitConfig(300L, 60L);

    // Whitelist paths (Bỏ qua hoàn toàn, không bao giờ rate limit)
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/payments/vnpay-callback",
            "/ws/**",
            "/topic/**",
            "/app/**"
    );

    // Giới hạn chi tiết cho từng endpoint nhạy cảm
    private final Map<String, RateLimitConfig> limits = new HashMap<>() {
        {
            put("/auth/login", new RateLimitConfig(10L, 10L));
            put("/auth/resend-verify", new RateLimitConfig(5L, 60L));
            put("/auth/signup", new RateLimitConfig(5L, 60L));
            put("/auth/verify", new RateLimitConfig(10L, 60L));
            put("/auth/refresh-token", new RateLimitConfig(15L, 60L));
            put("/auth/google/url", new RateLimitConfig(10L, 10L));
            put("/auth/callback/google", new RateLimitConfig(10L, 10L));
            put("/auth/forgot-password/send-otp", new RateLimitConfig(5L, 60L));
            put("/auth/forgot-password/verify-otp", new RateLimitConfig(10L, 60L));
            put("/auth/forgot-password/reset-password", new RateLimitConfig(10L, 60L));
            put("/events/public/search", new RateLimitConfig(100L, 10L));
            put("/events/trending", new RateLimitConfig(100L, 10L));
            put("/events/*/info", new RateLimitConfig(200L, 10L));
        }
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getServletPath();

        // 1. Kiểm tra danh sách bỏ qua (Bypass: VNPay webhook, WebSocket...)
        for (String excluded : EXCLUDED_PATHS) {
            if (pathMatcher.match(excluded, path)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        String ip = resolveClientIp(request);
        long now = System.currentTimeMillis();

        // 2. Bước 1: Kiểm tra Cầu chì tổng (Global IP Limit)
        String globalKey = "rate:ip:global:" + ip;
        if (!isAllowed(globalKey, GLOBAL_LIMIT, now)) {
            sendErrorResponse(request, response, GLOBAL_LIMIT.getWindowSec(), "Địa chỉ IP gửi quá nhiều yêu cầu, vui lòng thử lại sau!");
            return;
        }

        // 3. Bước 2: Kiểm tra Cầu chì chi tiết theo API (Endpoint-specific)
        RateLimitConfig endpointLimit = null;
        String matchedPattern = null;
        for (Map.Entry<String, RateLimitConfig> entry : limits.entrySet()) {
            if (pathMatcher.match(entry.getKey(), path)) {
                endpointLimit = entry.getValue();
                matchedPattern = entry.getKey();
                break;
            }
        }

        if (endpointLimit != null) {
            String endpointKey = "rate:ip:" + matchedPattern + ":" + ip;
            if (!isAllowed(endpointKey, endpointLimit, now)) {
                sendErrorResponse(request, response, endpointLimit.getWindowSec(), "Quá nhiều request, vui lòng thử lại!");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(String key, RateLimitConfig config, long now) {
        long windowStart = now - (config.getWindowSec() * 1000);
        String member = now + ":" + System.nanoTime();
        Long result = redisTemplate.execute(
                SCRIPT,
                Collections.singletonList(key),
                member,
                now,
                windowStart,
                config.getMaxRequests(),
                config.getWindowSec() + 2
        );
        return result != null && result == 1;
    }

    private String resolveClientIp(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(h -> h.split(",")[0].trim())
                .filter(h -> !h.isEmpty() && !"unknown".equalsIgnoreCase(h))
                .orElseGet(() -> {
                    String realIp = request.getHeader("X-Real-IP");
                    return (realIp != null && !realIp.isBlank()) ? realIp : request.getRemoteAddr();
                });
    }

    private void sendErrorResponse(HttpServletRequest request, HttpServletResponse response, long retryAfterSec, String message) throws IOException {
        ErrorResponse err = ErrorResponse.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .message(message)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSec));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(err));
    }
}
