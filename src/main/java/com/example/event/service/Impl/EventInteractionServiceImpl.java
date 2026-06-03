package com.example.event.service.Impl;

import com.example.event.constant.ErrorCode;
import com.example.event.constant.InteractionType;
import com.example.event.dto.response.EventTrendingResponse;
import com.example.event.entity.Event;
import com.example.event.entity.EventInteraction;
import com.example.event.entity.User;
import com.example.event.exception.AppException;
import com.example.event.projection.EventTrendingProjection;
import com.example.event.repository.EventInteractionRepository;
import com.example.event.repository.EventRepository;
import com.example.event.repository.UserRepository;
import com.example.event.service.EventInteractionService;
import com.example.event.service.RedisService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventInteractionServiceImpl implements EventInteractionService {
    private final EventInteractionRepository interactionRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    private static final String TRENDING_EVENTS_KEY = "events:trending";
    private static final long TRENDING_EVENTS_TTL = 86400L; 

    @Override
    @Transactional
    public void addInteraction(String eventId, String userId, InteractionType type) {
        Event event = Optional.ofNullable(eventRepository.findEventById(eventId))
            .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));

        User user = Optional.ofNullable(userRepository.findUserById(userId))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // VIEW — giới hạn lưu tối đa 1 lượt xem mỗi 24 giờ cho cùng 1 người dùng/sự kiện
        if (type == InteractionType.VIEW) {
            LocalDateTime limitTime = LocalDateTime.now().minusDays(1);
            boolean alreadyViewed = interactionRepository
                    .existsByEvent_IdAndUser_IdAndTypeAndCreatedAtAfter(eventId, userId, type, limitTime);
            if (alreadyViewed) return;
        }

        // FAVORITE — toggle (bấm lần 2 thì xóa)
        if (type == InteractionType.FAVORITE) {
            boolean alreadyFavorited = interactionRepository
                    .existsByEvent_IdAndUser_IdAndType(eventId, userId, type);
            if (alreadyFavorited) {
                interactionRepository.deleteByEvent_IdAndUser_IdAndType(eventId, userId, type);
                return;
            }
        }

        EventInteraction interaction = new EventInteraction();
        interaction.setEvent(event);
        interaction.setType(type);
        interaction.setUser(user);
        interactionRepository.save(interaction);
    }

    @Override
    public List<EventTrendingResponse> getTopTrendingEvents() {
        try {
            String cachedJson = redisService.get(TRENDING_EVENTS_KEY, String.class);
            if (cachedJson != null) {
                log.info("Fetching trending events from Redis cache.");
                return objectMapper.readValue(cachedJson, new TypeReference<List<EventTrendingResponse>>() {});
            }
        } catch (Exception e) {
            log.error("Failed to read trending events from Redis cache: {}", e.getMessage());
        }

        log.info("Trending events cache miss. Fetching from database.");
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<EventTrendingProjection> projections = interactionRepository.findTrendingEvents(since, 10);

        List<EventTrendingResponse> responses = projections.stream()
                .map(EventTrendingResponse::fromProjection)
                .collect(Collectors.toList());

        try {
            String jsonToCache = objectMapper.writeValueAsString(responses);
            redisService.set(TRENDING_EVENTS_KEY, jsonToCache, TRENDING_EVENTS_TTL);
            log.info("Successfully cached trending events in Redis with 1 day TTL.");
        } catch (Exception e) {
            log.error("Failed to cache trending events in Redis: {}", e.getMessage());
        }

        return responses;
    }
}
