package com.example.event.service.Impl;

import com.example.event.constant.OutboxStatus;
import com.example.event.entity.OutboxMessage;
import com.example.event.repository.OutboxMessageRepository;
import com.example.event.service.OutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, String aggregateId, String eventType, String destination, Map<String, String> payloadMap) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            OutboxMessage message = OutboxMessage.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .destination(destination)
                    .payload(jsonPayload)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(5)
                    .createdAt(LocalDateTime.now())
                    .build();

            outboxMessageRepository.save(message);
            log.info("[OUTBOX] Đã lưu outbox message id={} aggregate={}:{} event={}",
                    message.getId(), aggregateType, aggregateId, eventType);
        } catch (JsonProcessingException e) {
            log.error("[OUTBOX] Lỗi serialize payload sang JSON: aggregateId={}, eventType={}", aggregateId, eventType, e);
            throw new RuntimeException("Không thể serialize outbox payload", e);
        }
    }
}
