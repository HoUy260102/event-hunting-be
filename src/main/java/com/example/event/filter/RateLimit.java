package com.example.event.filter;

import com.example.event.dto.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javafx.util.Pair;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimit extends OncePerRequestFilter {
    private final RedisTemplate<String, Object> redisTemplate;

    Map<String, Pair<Long, Long>> limits = new HashMap<>(){
        {
            put("/auth/login", new Pair<>(3L,10L));
            put("/auth/resend-verify", new Pair<>(1L, 60L));
        }
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        Pair<Long, Long> limit = limits.get(path);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long maxRequests = limit.getKey();
        long windowSec = limit.getValue();

        long now = System.currentTimeMillis();
        long windowStart = now - (windowSec * 1000);
        String member = now + ":" + System.nanoTime();
        String ip = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(h -> h.split(",")[0].trim())
                .orElse(request.getRemoteAddr());

        String key = "rate:path:" + path  + ":" + ip;

        redisTemplate.opsForZSet().add(key, member, now);
        redisTemplate.expire(key, windowSec + 2, TimeUnit.SECONDS);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        Long count = redisTemplate.opsForZSet().zCard(key);

        if (count != null && count > maxRequests) {
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
