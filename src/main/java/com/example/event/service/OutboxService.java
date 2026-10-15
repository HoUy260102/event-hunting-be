package com.example.event.service;

import java.util.Map;

public interface OutboxService {
    void publish(String aggregateType, String aggregateId, String eventType, String destination,
            Map<String, String> payloadMap);
}
