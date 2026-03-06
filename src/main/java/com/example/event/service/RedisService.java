package com.example.event.service;

public interface RedisService {
    <T> void set(String key, T value, Long ttl);
    <T> T get(String key, Class<T> tClass);
    void del(String key);
    boolean exists(String key);
    Long incr(String key, int by);
    void expire(String key, int seconds);
}
