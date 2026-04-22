package com.example.event.job;

import com.example.event.repository.ShowRepository;
import com.example.event.service.TicketQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class TicketQueue {
    private final RedisTemplate<String, String> redis;

    private final TicketQueueService ticketQueueService;
    private final ShowRepository showRepository;
    private static final int CLEANUP_THRESHOLD_DAYS = 10;

    private static final String WAITING = "waiting:show:%s";
    private static final String BUYING = "buying:show:%s";
    private static final String ACTIVE_SHOWS_KEY = "active_shows";

    @Scheduled(fixedRate = 28800000)
    public void cleanUpActiveShows() {
        LocalDateTime thresholdDate = LocalDateTime.now().minusDays(CLEANUP_THRESHOLD_DAYS);
        Pageable limit = PageRequest.of(0, 100);
        List<String> cleanShowIds = showRepository.findExpiredShows(thresholdDate, limit)
                .stream()
                .map(show -> show.getId())
                .collect(Collectors.toList());
        if (cleanShowIds != null && !cleanShowIds.isEmpty()) {
            String[] idsArray = cleanShowIds.toArray(new String[0]);
            redis.opsForZSet().remove(ACTIVE_SHOWS_KEY, idsArray);
            showRepository.markAsCleanedUp(cleanShowIds);
            log.info("Đã dọn dẹp {} show quá hạn khỏi Redis và DB", cleanShowIds.size());
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void processQueueDrain() {
        Set<String> activeShows = redis.opsForZSet().range(ACTIVE_SHOWS_KEY, 0, 9);
        if (activeShows == null || activeShows.isEmpty()) return;

        for (String showId : activeShows) {
            try {
                ticketQueueService.drainQueue(showId);
                redis.opsForZSet().add(ACTIVE_SHOWS_KEY, showId, System.currentTimeMillis());
            } catch (Exception e) {
                log.error("Lỗi khi drain queue cho show {}: {}", showId, e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void processQueueCleanup() {
        Set<String> activeShows = redis.opsForZSet().range(ACTIVE_SHOWS_KEY, 0, 9);
        if (activeShows == null || activeShows.isEmpty()) return;

        for (String showId : activeShows) {
            try {
                ticketQueueService.cleanupGhostUsers(showId);

                Long waitingCount = redis.opsForZSet().zCard(String.format(WAITING, showId));
                Long buyingCount = redis.opsForZSet().zCard(String.format(BUYING, showId));
                if ((waitingCount == null || waitingCount == 0) && (buyingCount == null || buyingCount == 0)) {
                    redis.opsForZSet().remove(ACTIVE_SHOWS_KEY, showId);
                } else {
                    redis.opsForZSet().add(ACTIVE_SHOWS_KEY, showId, System.currentTimeMillis());
                }
            } catch (Exception e) {
                log.error("Lỗi khi cleanup cho show {}: {}", showId, e.getMessage());
            }
        }
    }

}
