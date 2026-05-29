package com.example.event.service.Impl;

import com.example.event.service.SearchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SearchHistoryServiceImpl implements SearchHistoryService {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String HISTORY_KEY_PREFIX = "search:history:";
    private static final long MAX_HISTORY_SIZE = 10;
    private static final long HISTORY_TTL_DAYS = 30;

    @Override
    public void saveSearchQuery(String userId, String query) {
        if (query == null || query.trim().isEmpty()) return;
        String key = HISTORY_KEY_PREFIX + userId;
        String trimmedQuery = query.trim();

        // Add search query to Redis ZSet with current timestamp as score
        redisTemplate.opsForZSet().add(key, trimmedQuery, System.currentTimeMillis());

        // Trim the sorted set to only retain the 10 most recent searches
        Long size = redisTemplate.opsForZSet().size(key);
        if (size != null && size > MAX_HISTORY_SIZE) {
            redisTemplate.opsForZSet().removeRange(key, 0, size - MAX_HISTORY_SIZE - 1);
        }

        // Set key expiration to 30 days
        redisTemplate.expire(key, HISTORY_TTL_DAYS, TimeUnit.DAYS);
    }

    @Override
    public List<String> getSearchHistory(String userId) {
        String key = HISTORY_KEY_PREFIX + userId;
        // Retrieve range from newest (highest score) to oldest (lowest score)
        Set<Object> historySet = redisTemplate.opsForZSet().reverseRange(key, 0, MAX_HISTORY_SIZE - 1);
        if (historySet == null) return new ArrayList<>();

        List<String> historyList = new ArrayList<>();
        for (Object obj : historySet) {
            historyList.add(obj.toString());
        }
        return historyList;
    }

    @Override
    public void deleteSearchQuery(String userId, String query) {
        String key = HISTORY_KEY_PREFIX + userId;
        redisTemplate.opsForZSet().remove(key, query);
    }

    @Override
    public void clearSearchHistory(String userId) {
        String key = HISTORY_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }
}
