package com.example.event.service.Impl;

import com.example.event.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public <T> void set(String key, T value, Long ttl) {
        if (ttl > 0) {
            redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, value);
        }
    }

    @Override
    public <T> void set(String key, T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public <T> T get(String key, Class<T> tClass) {
        Object val = redisTemplate.opsForValue().get(key);
        if (val == null) return null;
        if (Number.class.isAssignableFrom(tClass) && val instanceof Number) {
            Number numberVal = (Number) val;
            if (tClass == Long.class) {
                return tClass.cast(numberVal.longValue());
            } else if (tClass == Integer.class) {
                return tClass.cast(numberVal.intValue());
            } else if (tClass == Double.class) {
                return tClass.cast(numberVal.doubleValue());
            }
        }
        return tClass.cast(val);
    }

    @Override
    public void del(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public boolean exists(String key) {
        return redisTemplate.hasKey(key);
    }

    @Override
    public Long incr(String key, int by) {
        return redisTemplate.opsForValue().increment(key, by);
    }

    @Override
    public void expire(String key, int seconds) {
        redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }
}
