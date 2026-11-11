package com.example.event.component;

import com.example.event.config.RedisStreamConfig;
import com.example.event.constant.EventStatus;
import com.example.event.entity.Event;
import com.example.event.repository.EventRepository;
import com.example.event.service.QdrantClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventQdrantConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final QdrantClientService qdrantClientService;
    private final EventRepository eventRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String recordId = record.getId().getValue();
        Map<String, String> value = record.getValue();

        // Bỏ qua bản ghi init
        if (value.containsKey("_init")) {
            ack(record);
            return;
        }

        String eventId = value.get("eventId");
        String action = value.getOrDefault("action", "UPSERT");

        if (eventId == null || eventId.isBlank()) {
            log.warn("[QDRANT-CONSUMER] Record {} thiếu eventId. Tiến hành ACK bỏ qua.", recordId);
            ack(record);
            return;
        }

        log.info("[QDRANT-CONSUMER] Bắt đầu xử lý record={} cho eventId={}, action={}",
                recordId, eventId, action);

        try {
            if ("DELETE".equalsIgnoreCase(action)) {
                qdrantClientService.deleteEvent(eventId);
            } else {
                Event event = eventRepository.findEventByIdForDetails(eventId);
                if (event == null || event.getDeletedAt() != null || event.getStatus() != EventStatus.PUBLISHED) {
                    log.info("[QDRANT-CONSUMER] Event {} không tồn tại hoặc không ở trạng thái PUBLISHED. Xóa khỏi Qdrant.", eventId);
                    qdrantClientService.deleteEvent(eventId);
                } else {
                    qdrantClientService.upsertEvent(event);
                }
            }

            ack(record);
            log.info("[QDRANT-CONSUMER] Xử lý và ACK thành công record={} (eventId={})", recordId, eventId);

        } catch (Exception e) {
            log.error("[QDRANT-CONSUMER] Xử lý thất bại record={} (eventId={}): {}",
                    recordId, eventId, e.getMessage(), e);
            // Không ACK để RedisStreamRecoveryJob quét lại từ Pending list
        }
    }

    private void ack(MapRecord<String, String, String> record) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(
                    RedisStreamConfig.EVENT_QDRANT_STREAM_KEY,
                    RedisStreamConfig.EVENT_QDRANT_GROUP_NAME,
                    record.getId());
        } catch (Exception e) {
            log.error("[QDRANT-CONSUMER] Lỗi ACK record {}: {}", record.getId(), e.getMessage());
        }
    }
}

