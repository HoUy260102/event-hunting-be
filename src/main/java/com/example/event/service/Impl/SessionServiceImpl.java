package com.example.event.service.Impl;

import com.example.event.service.RedisService;
import com.example.event.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {
    private final RedisService redisService;
    private String prefix = "blacklist:sid:";
    @Override
    public void addToBlackList(String sid, long duration) {
        String key = prefix + sid;
        redisService.set(key, "", duration);
    }

    @Override
    public boolean isBlackList(String sid) {
        String key = prefix + sid;
        return redisService.exists(key);
    }
}
