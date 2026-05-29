package com.example.event.service;

import java.util.List;

public interface SearchHistoryService {
    void saveSearchQuery(String userId, String query);
    List<String> getSearchHistory(String userId);
    void deleteSearchQuery(String userId, String query);
    void clearSearchHistory(String userId);
}
