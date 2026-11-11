package com.example.event.service.Impl;

import com.example.event.constant.ErrorCode;
import com.example.event.constant.EventStatus;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
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
        List<String> eventIds = null;

        // 1. Thử lấy danh sách ID sự kiện xu hướng từ cache Redis
        try {
            String cachedJson = redisService.get(TRENDING_EVENTS_KEY, String.class);
            if (cachedJson != null && !cachedJson.isBlank()) {
                if (cachedJson.trim().startsWith("[{")) {
                    // Dữ liệu cache cũ dạng full object -> Xóa để nâng cấp sang lưu List<String> ID
                    redisService.del(TRENDING_EVENTS_KEY);
                } else {
                    eventIds = objectMapper.readValue(cachedJson, new TypeReference<List<String>>() {});
                    log.info("[INTERACTION] Lấy danh sách ID sự kiện xu hướng từ cache Redis: {}", eventIds);
                }
            }
        } catch (Exception e) {
            log.error("[INTERACTION] Lỗi đọc ID sự kiện xu hướng từ cache Redis: {}", e.getMessage());
        }

        // 2. Cache miss hoặc rỗng -> Tính toán thứ hạng và gom 10 ID sự kiện từ Database
        if (eventIds == null || eventIds.isEmpty()) {
            log.info("[INTERACTION] Cache miss sự kiện xu hướng. Đang tính toán thứ hạng từ cơ sở dữ liệu.");
            LocalDateTime since = LocalDateTime.now().minusDays(30);
            List<EventTrendingProjection> projections = interactionRepository.findTrendingEvents(since, 10);

            eventIds = projections.stream()
                    .map(EventTrendingProjection::getId)
                    .collect(Collectors.toList());

            if (!eventIds.isEmpty()) {
                try {
                    String jsonToCache = objectMapper.writeValueAsString(eventIds);
                    redisService.set(TRENDING_EVENTS_KEY, jsonToCache, TRENDING_EVENTS_TTL);
                    log.info("[INTERACTION] Đã lưu danh sách ID sự kiện xu hướng vào Redis (TTL 1 ngày).");
                } catch (Exception e) {
                    log.error("[INTERACTION] Lỗi lưu ID sự kiện xu hướng vào Redis cache: {}", e.getMessage());
                }
            }
        }

        if (eventIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Lấy thông tin chi tiết sự kiện mới nhất từ MySQL theo danh sách ID (Primary Key IN query)
        List<Event> events = eventRepository.findEventsByIdsWithDetails(eventIds);
        Map<String, Event> eventMap = events.stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        // 4. Giữ đúng thứ tự xếp hạng (Ranking) và lọc bỏ sự kiện bị hủy / xóa / đã kết thúc
        LocalDateTime now = LocalDateTime.now();
        List<EventTrendingResponse> responses = new ArrayList<>();
        for (String id : eventIds) {
            Event event = eventMap.get(id);
            if (event != null 
                    && event.getDeletedAt() == null 
                    && event.getStatus() == EventStatus.PUBLISHED 
                    && event.getEndTime() != null 
                    && event.getEndTime().isAfter(now)) {
                responses.add(EventTrendingResponse.fromEvent(event));
            }
        }

        return responses;
    }
}
