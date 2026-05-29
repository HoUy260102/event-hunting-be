package com.example.event.filter;

import com.example.event.dto.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.AntPathMatcher;

@Component
@RequiredArgsConstructor
public class RateLimit extends OncePerRequestFilter {
    private final RedisTemplate<String, Object> redisTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final String LUA_SCRIPT =
            "local key = KEYS[1] " +
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

    private final Map<String, RateLimitConfig> limits = new HashMap<>() {
        {
            put("/auth/login", new RateLimitConfig(3L, 10L));
            put("/auth/resend-verify", new RateLimitConfig(1L, 60L));
            put("/auth/signup", new RateLimitConfig(3L, 60L));
            put("/auth/verify", new RateLimitConfig(5L, 60L));
            put("/auth/refresh-token", new RateLimitConfig(5L, 10L));
            put("/auth/google/url", new RateLimitConfig(5L, 10L));
            put("/auth/callback/google", new RateLimitConfig(5L, 10L));
            put("/events/public/search", new RateLimitConfig(10L, 10L));
            put("/events/trending", new RateLimitConfig(10L, 10L));
            put("/events/*/info", new RateLimitConfig(20L, 10L));
        }
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        
        RateLimitConfig limit = null;
        String matchedPattern = null;
        for (Map.Entry<String, RateLimitConfig> entry : limits.entrySet()) {
            if (pathMatcher.match(entry.getKey(), path)) {
                limit = entry.getValue();
                matchedPattern = entry.getKey();
                break;
            }
        }

        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long maxRequests = limit.getMaxRequests();
        long windowSec = limit.getWindowSec();

        long now = System.currentTimeMillis();
        long windowStart = now - (windowSec * 1000);
        String member = now + ":" + System.nanoTime();
        
        String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(h -> h.split(",")[0].trim())
                .orElse(request.getRemoteAddr());

        // Smart device identification to support NAT/cafe environments
        String deviceId = request.getHeader("X-Device-Id");
        String clientIdentifier;
        if (deviceId != null && !deviceId.trim().isEmpty() && !"unknownDevice".equalsIgnoreCase(deviceId)) {
            clientIdentifier = ip + ":" + deviceId.trim();
        } else {
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null) {
                clientIdentifier = ip + ":" + Math.abs(userAgent.hashCode());
            } else {
                clientIdentifier = ip;
            }
        }

        String key = "rate:device:" + matchedPattern  + ":" + clientIdentifier;

        if ("/auth/resend-verify".equals(matchedPattern)) {
            String email = request.getParameter("email");
            if (email != null && !email.trim().equals("")) {
                key += ":" + email;
            }
        }

        Long result = redisTemplate.execute(
                SCRIPT,
                Collections.singletonList(key),
                member,
                now,
                windowStart,
                maxRequests,
                windowSec + 2
        );

        if (result != null && result == 0) {
            ErrorResponse err = ErrorResponse.builder()
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .message("Quá nhiều request, vui lòng thử lại!")
                    .timestamp(LocalDateTime.now())
                    .build();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            String json = new ObjectMapper().writeValueAsString(err);
            response.getWriter().write(json);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
