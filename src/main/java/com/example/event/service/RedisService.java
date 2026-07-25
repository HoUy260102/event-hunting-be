package com.example.event.service;

public interface RedisService {
    <T> void set(String key, T value, Long ttl);
    <T> void set(String key, T value);
    <T> T get(String key, Class<T> tClass);
    void del(String key);
    boolean exists(String key);
    boolean setIfAbsent(String key, String value, Long ttl);
    Long incr(String key, int by);
    void expire(String key, int seconds);
}
