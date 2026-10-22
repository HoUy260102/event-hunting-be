package com.example.event.service;

import com.example.event.dto.response.QueueStatusResponse;

public interface TicketQueueService {
    QueueStatusResponse joinQueue(String showId);

    QueueStatusResponse getStatus(String showId);

    void leaveQueue(String showId);

    void leaveQueue(String showId, String userId);

    boolean validateQueueAccess(String showId);

    long getRemainingTimeSeconds(String showId, String userId);

    // Worker dọn rác
    void cleanupGhostUsers(String showId);

    void drainQueue(String showId);
}
