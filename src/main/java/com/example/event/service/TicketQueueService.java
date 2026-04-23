package com.example.event.service;

import java.util.Map;

public interface TicketQueueService {
    Map<String, Object> joinQueue(String showId);
    Map<String, Object> getStatus(String showId);
    void leaveQueue(String showId);
    boolean validateQueueToken(String showId, String clientToken);
    long getRemainingTimeSeconds(String showId, String userId);

    //Worker dọn rác
    void cleanupGhostUsers(String showId);
    void drainQueue(String showId);
}
