package com.example.event.filter;

import com.example.event.config.security.user.CustomUserDetails;
import com.example.event.dto.response.ErrorResponse;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserRateLimit extends OncePerRequestFilter {
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
            // Public read APIs
            put("/events/public/search", new RateLimitConfig(50L, 10L));
            put("/events/trending", new RateLimitConfig(30L, 10L));
            put("/events/*/info", new RateLimitConfig(30L, 10L));

            // Transactional/Sensitive APIs
            put("/reservations", new RateLimitConfig(5L, 10L));
            put("/reservations/*/cancel", new RateLimitConfig(5L, 10L));
            put("/payments/create_payment_url", new RateLimitConfig(5L, 10L));
            put("/favorites/*", new RateLimitConfig(10L, 10L));
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

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            // If not authenticated, Layer 2 does not apply (Layer 1 already covers it)
            filterChain.doFilter(request, response);
            return;
        }

        String userId = null;
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            userId = ((CustomUserDetails) principal).getUser().getId();
        }

        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long maxRequests = limit.getMaxRequests();
        long windowSec = limit.getWindowSec();

        long now = System.currentTimeMillis();
        long windowStart = now - (windowSec * 1000);
        String member = now + ":" + System.nanoTime();

        String key = "rate:user:" + matchedPattern + ":" + userId;

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
                    .message("Tài khoản của bạn đang gửi quá nhiều yêu cầu, vui lòng thử lại sau!")
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
