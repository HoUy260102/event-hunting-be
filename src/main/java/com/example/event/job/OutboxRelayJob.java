package com.example.event.job;

import com.example.event.constant.OutboxStatus;
import com.example.event.entity.OutboxMessage;
import com.example.event.repository.OutboxMessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final OutboxMessageRepository outboxMessageRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 50;

    /**
     * Định kỳ quét các message PENDING và đẩy vào Redis Stream.
     * ShedLock đảm bảo trong cụm nhiều instance chỉ có 1 worker quét tại một thời điểm.
     */
    @Scheduled(fixedDelay = 1000)
    @SchedulerLock(
            name = "outboxRelayJob",
            lockAtMostFor = "PT30S",
            lockAtLeastFor = "PT1S"
    )
    public void processOutboxMessages() {
        List<OutboxMessage> pendingMessages = outboxMessageRepository.findPendingMessages(
                OutboxStatus.PENDING,
                PageRequest.of(0, BATCH_SIZE)
        );

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.debug("[OUTBOX-RELAY] Bắt đầu xử lý {} messages PENDING", pendingMessages.size());

        for (OutboxMessage msg : pendingMessages) {
            relaySingleMessage(msg);
        }
    }

    private void relaySingleMessage(OutboxMessage msg) {
        try {
            // Deserialize JSON Map<String, String> để push vào Redis Stream
            Map<String, String> streamFields = objectMapper.readValue(
                    msg.getPayload(),
                    new TypeReference<Map<String, String>>() {}
            );

            RecordId recordId = stringRedisTemplate.opsForStream()
                    .add(msg.getDestination(), streamFields);

            msg.setStatus(OutboxStatus.COMPLETED);
            msg.setProcessedAt(LocalDateTime.now());
            msg.setLastError(null);
            outboxMessageRepository.save(msg);

            log.info("[OUTBOX-RELAY] Đã chuyển message id={} aggregate={}:{} sang Redis Stream {} (recordId={})",
                    msg.getId(), msg.getAggregateType(), msg.getAggregateId(), msg.getDestination(), recordId);

        } catch (Exception e) {
            int retries = msg.getRetryCount() + 1;
            msg.setRetryCount(retries);
            msg.setLastError(e.getMessage() != null ? e.getMessage() : e.toString());

            if (retries >= msg.getMaxRetries()) {
                msg.setStatus(OutboxStatus.FAILED);
                log.error("[OUTBOX-RELAY] Message id={} vượt quá số lần retry ({}/{}), đánh dấu FAILED: {}",
                        msg.getId(), retries, msg.getMaxRetries(), e.getMessage());
            } else {
                log.warn("[OUTBOX-RELAY] Lỗi chuyển message id={} (lần thử {}/{}): {}",
                        msg.getId(), retries, msg.getMaxRetries(), e.getMessage());
            }

            outboxMessageRepository.save(msg);
        }
    }

    private static final int CLEANUP_CHUNK_SIZE = 500;
    private static final long CLEANUP_SLEEP_MS = 50L;

    /**
     * Dọn dẹp các bản ghi Outbox đã COMPLETED sau 3 ngày theo từng chunk nhỏ (500 bản ghi/lần).
     * Tránh tình trạng khóa bảng diện rộng và nghẽn I/O Database.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(
            name = "outboxCleanupJob",
            lockAtMostFor = "PT2H",
            lockAtLeastFor = "PT1M"
    )
    public void cleanupOldOutboxMessages() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);
        int totalDeleted = 0;
        Pageable pageable = PageRequest.of(0, CLEANUP_CHUNK_SIZE);

        log.info("[OUTBOX-CLEANUP] Bắt đầu cleanup outbox messages COMPLETED trước {}", threshold);

        while (true) {
            List<String> idsToDelete = outboxMessageRepository.findOldCompletedMessageIds(
                    OutboxStatus.COMPLETED,
                    threshold,
                    pageable
            );

            if (idsToDelete == null || idsToDelete.isEmpty()) {
                break;
            }

            try {
                int deletedCount = deleteBatch(idsToDelete);
                totalDeleted += deletedCount;
                log.debug("[OUTBOX-CLEANUP] Đã xóa batch {} bản ghi (tổng: {})", deletedCount, totalDeleted);

                if (CLEUP_SLEEP_NEEDED(idsToDelete.size())) {
                    Thread.sleep(CLEANUP_SLEEP_MS);
                }
            } catch (InterruptedException e) {
                log.warn("[OUTBOX-CLEANUP] Job bị gián đoạn: {}", e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[OUTBOX-CLEANUP] Lỗi khi xóa batch outbox: {}", e.getMessage(), e);
                break;
            }
        }

        if (totalDeleted > 0) {
            log.info("[OUTBOX-CLEANUP] Hoàn tất dọn dẹp. Tổng số bản ghi outbox đã xóa: {}", totalDeleted);
        }
    }

    @Transactional
    public int deleteBatch(List<String> ids) {
        return outboxMessageRepository.deleteByIds(ids);
    }

    private boolean CLEUP_SLEEP_NEEDED(int currentBatchSize) {
        return currentBatchSize >= CLEANUP_CHUNK_SIZE;
    }
}
