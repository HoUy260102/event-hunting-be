package com.example.event.component;

import com.example.event.config.RedisStreamConfig;
import com.example.event.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventQdrantProducer {

    private final OutboxService outboxService;

    public void syncEvent(String eventId, String action) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }

        try {
            Map<String, String> payload = Map.of(
                    "eventId", eventId.trim(),
                    "action", action != null ? action.trim() : "UPSERT"
            );

            outboxService.publish("EVENT", eventId.trim(), "SYNC_QDRANT",
                    RedisStreamConfig.EVENT_QDRANT_STREAM_KEY, payload);

            log.info("[QDRANT-PRODUCER] Đã queue sự kiện Outbox SYNC_QDRANT (eventId={}, action={})",
                    eventId, action);

        } catch (Exception e) {
            log.error("[QDRANT-PRODUCER] Lỗi lưu sự kiện Outbox SYNC_QDRANT (eventId={}): {}",
                    eventId, e.getMessage(), e);
            throw new RuntimeException("Lỗi lưu sự kiện đồng bộ Qdrant vào Outbox", e);
        }
    }
}

