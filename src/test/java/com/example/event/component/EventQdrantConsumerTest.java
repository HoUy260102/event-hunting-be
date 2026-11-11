package com.example.event.component;

import com.example.event.config.RedisStreamConfig;
import com.example.event.constant.EventStatus;
import com.example.event.entity.Event;
import com.example.event.repository.EventRepository;
import com.example.event.service.QdrantClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventQdrantConsumerTest {

    @Mock
    private QdrantClientService qdrantClientService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @InjectMocks
    private EventQdrantConsumer eventQdrantConsumer;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    @Test
    @DisplayName("Should skip and ACK init record")
    void testOnMessage_InitRecord() {
        MapRecord<String, String, String> record = MapRecord.create(
                RedisStreamConfig.EVENT_QDRANT_STREAM_KEY,
                Collections.singletonMap("_init", "1")
        ).withId(RecordId.of("1-0"));

        eventQdrantConsumer.onMessage(record);

        verify(qdrantClientService, never()).upsertEvent(any());
        verify(qdrantClientService, never()).deleteEvent(any());
        verify(streamOperations).acknowledge(eq(RedisStreamConfig.EVENT_QDRANT_STREAM_KEY),
                eq(RedisStreamConfig.EVENT_QDRANT_GROUP_NAME), eq(RecordId.of("1-0")));
    }

    @Test
    @DisplayName("Should call upsertEvent and ACK when event is PUBLISHED")
    void testOnMessage_UpsertPublishedEvent() {
        String eventId = "01HP7Z9XYZABCDEF1234567890";
        MapRecord<String, String, String> record = MapRecord.create(
                RedisStreamConfig.EVENT_QDRANT_STREAM_KEY,
                Map.of("eventId", eventId, "action", "UPSERT")
        ).withId(RecordId.of("2-0"));

        Event event = new Event();
        event.setId(eventId);
        event.setStatus(EventStatus.PUBLISHED);

        when(eventRepository.findEventByIdForDetails(eventId)).thenReturn(event);

        eventQdrantConsumer.onMessage(record);

        verify(qdrantClientService).upsertEvent(event);
        verify(streamOperations).acknowledge(eq(RedisStreamConfig.EVENT_QDRANT_STREAM_KEY),
                eq(RedisStreamConfig.EVENT_QDRANT_GROUP_NAME), eq(RecordId.of("2-0")));
    }

    @Test
    @DisplayName("Should call deleteEvent and ACK when action is DELETE")
    void testOnMessage_DeleteAction() {
        String eventId = "01HP7Z9XYZABCDEF1234567890";
        MapRecord<String, String, String> record = MapRecord.create(
                RedisStreamConfig.EVENT_QDRANT_STREAM_KEY,
                Map.of("eventId", eventId, "action", "DELETE")
        ).withId(RecordId.of("3-0"));

        eventQdrantConsumer.onMessage(record);

        verify(qdrantClientService).deleteEvent(eventId);
        verify(streamOperations).acknowledge(eq(RedisStreamConfig.EVENT_QDRANT_STREAM_KEY),
                eq(RedisStreamConfig.EVENT_QDRANT_GROUP_NAME), eq(RecordId.of("3-0")));
    }
}

