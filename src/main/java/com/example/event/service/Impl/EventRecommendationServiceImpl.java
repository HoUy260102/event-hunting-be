package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.EventStatus;
import com.example.event.constant.InteractionType;
import com.example.event.dto.EventSearchPublicDTO;
import com.example.event.dto.response.EventTrendingResponse;
import com.example.event.entity.Event;
import com.example.event.entity.EventInteraction;
import com.example.event.mapper.EventMapper;
import com.example.event.repository.EventInteractionRepository;
import com.example.event.repository.EventRepository;
import com.example.event.repository.FavoriteRepository;
import com.example.event.service.EventInteractionService;
import com.example.event.service.EventRecommendationService;
import com.example.event.service.RedisService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventRecommendationServiceImpl implements EventRecommendationService {

    private final EventInteractionRepository interactionRepository;
    private final EventRepository eventRepository;
    private final FavoriteRepository favoriteRepository;
    private final RedisService redisService;
    private final SecurityUtils securityUtils;
    private final EventMapper eventMapper;
    private final ObjectMapper objectMapper;
    private final EventInteractionService eventInteractionService;

    private static final String REC_KEY_PREFIX = "user:recommendation:";
    private static final long REC_TTL_SECONDS = 86400L; // 1 ngày

    /**
     * Tác vụ lập lịch chạy ngầm định kỳ hàng ngày vào lúc 2 giờ sáng.
     * Mặc định quét dữ liệu tương tác trong vòng 90 ngày qua.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void runDailyRecommendationJob() {
        log.info("[RECOMMENDATION] Bắt đầu chạy Job lập lịch gợi ý hàng ngày (mặc định 90 ngày).");
        runRecommendationJob(90);
    }

    /**
     * Lắng nghe sự kiện ứng dụng đã sẵn sàng (Startup thành công) để chạy ngay lập tức.
     * Điều này giúp việc kiểm thử (Testing) diễn ra tức thời mà không cần đợi đến 2h sáng.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[RECOMMENDATION] Ứng dụng đã khởi động thành công. Kích hoạt tính toán gợi ý ngay lập tức.");
        // Chạy bất đồng bộ trên một luồng riêng để tránh làm nghẽn/chậm quá trình khởi chạy của Spring Boot
        new Thread(() -> {
            try {
                runRecommendationJob(90);
            } catch (Exception e) {
                log.error("[RECOMMENDATION] Lỗi khi tính toán gợi ý lúc khởi động: {}", e.getMessage());
            }
        }).start();
    }

    @Override
    @Transactional(readOnly = true)
    public void runRecommendationJob(int days) {
        log.info("[RECOMMENDATION] Bắt đầu tính toán ma trận gợi ý dựa trên dữ liệu tương tác {} ngày qua.", days);
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(days);
        
        // 1. Lấy dữ liệu tương tác đã được gom nhóm và tính trọng số lớn nhất từ Database
        List<Object[]> aggregatedInteractions = interactionRepository.findAggregatedInteractions(sinceDate);
        if (aggregatedInteractions.isEmpty()) {
            log.warn("[RECOMMENDATION] Không tìm thấy dữ liệu tương tác nào trong vòng {} ngày qua. Huỷ bỏ job.", days);
            return;
        }

        // 2. Xây dựng ma trận User - Event
        Map<String, Map<String, Double>> userEventMatrix = new HashMap<>();
        for (Object[] row : aggregatedInteractions) {
            String userId = (String) row[0];
            String eventId = (String) row[1];
            Double weight = ((Number) row[2]).doubleValue();

            userEventMatrix.computeIfAbsent(userId, k -> new HashMap<>())
                    .put(eventId, weight);
        }

        // 3. Tính toán gợi ý cho từng người dùng hoạt động
        for (String userId : userEventMatrix.keySet()) {
            try {
                List<String> recommendedEventIds = calculateRecommendationsForUser(userId, userEventMatrix);
                
                // Lưu danh sách ID sự kiện gợi ý dạng JSON vào Redis
                String redisKey = REC_KEY_PREFIX + userId;
                String jsonVal = objectMapper.writeValueAsString(recommendedEventIds);
                redisService.set(redisKey, jsonVal, REC_TTL_SECONDS);
                
                log.debug("[RECOMMENDATION] Đã lưu gợi ý cho User {}: {}", userId, recommendedEventIds);
            } catch (Exception e) {
                log.error("[RECOMMENDATION] Lỗi khi xử lý gợi ý cho User {}: {}", userId, e.getMessage());
            }
        }
        log.info("[RECOMMENDATION] Hoàn thành việc cập nhật toàn bộ gợi ý cá nhân hóa lên Redis.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventSearchPublicDTO> getPersonalizedRecommendations(int limit) {
        String userId = securityUtils.getCurrentUserId();
        
        // Trường hợp người dùng chưa đăng nhập -> Fallback sang sự kiện xu hướng (Trending)
        if (userId == null || userId.isEmpty()) {
            log.info("[RECOMMENDATION] Người dùng chưa đăng nhập. Trả về danh sách sự kiện xu hướng.");
            return getTrendingEventsFallback(limit);
        }

        String redisKey = REC_KEY_PREFIX + userId;
        String cachedJson = redisService.get(redisKey, String.class);

        // Trường hợp chưa có dữ liệu tính toán trước trong Redis -> Fallback sang sự kiện xu hướng
        if (cachedJson == null || cachedJson.isEmpty()) {
            log.info("[RECOMMENDATION] Không tìm thấy dữ liệu gợi ý cho User {} trong Redis. Trả về sự kiện xu hướng.", userId);
            return getTrendingEventsFallback(limit);
        }

        try {
            List<String> candidateEventIds = objectMapper.readValue(cachedJson, new TypeReference<List<String>>() {});
            if (candidateEventIds.isEmpty()) {
                return getTrendingEventsFallback(limit);
            }

            // [LỌC THỰC TẾ] Lấy các sự kiện mà người dùng CHƯA đăng ký mua vé (Real-time check)
            List<EventInteraction> registrations = interactionRepository.findByUser_IdAndType(userId, InteractionType.REGISTER);
            Set<String> registeredEventIds = registrations.stream()
                    .map(ri -> ri.getEvent().getId())
                    .collect(Collectors.toSet());

            List<String> finalRecommendedIds = candidateEventIds.stream()
                    .filter(eventId -> !registeredEventIds.contains(eventId)) // Chỉ giữ lại các sự kiện CHƯA đăng ký
                    .limit(limit)
                    .collect(Collectors.toList());

            if (finalRecommendedIds.isEmpty()) {
                return getTrendingEventsFallback(limit);
            }

            // Lấy thông tin chi tiết các sự kiện gợi ý cuối cùng từ Database (Batch fetch để tránh N+1)
            List<EventSearchPublicDTO> dtos = new ArrayList<>();
            if (!finalRecommendedIds.isEmpty()) {
                List<Event> events = eventRepository.findEventsByIdsWithDetails(finalRecommendedIds);
                Map<String, Event> eventMap = events.stream()
                        .collect(Collectors.toMap(Event::getId, event -> event));
                for (String eventId : finalRecommendedIds) {
                    Event event = eventMap.get(eventId);
                    if (event != null 
                            && event.getDeletedAt() == null 
                            && event.getStatus() == EventStatus.PUBLISHED 
                            && event.getEndTime().isAfter(LocalDateTime.now())) {
                        dtos.add(eventMapper.toSearchPublicDTO(event));
                    }
                }
            }

            // Map trạng thái IsSaved của người dùng vào mỗi sự kiện
            if (!dtos.isEmpty()) {
                List<String> eventIds = dtos.stream().map(EventSearchPublicDTO::getId).collect(Collectors.toList());
                Set<String> savedEventIds = favoriteRepository.findSavedEventIds(userId, eventIds);
                dtos.forEach(dto -> dto.setIsSaved(savedEventIds.contains(dto.getId())));
            }

            return dtos;

        } catch (Exception e) {
            log.error("[RECOMMENDATION] Lỗi khi giải mã danh sách gợi ý của User {}: {}", userId, e.getMessage());
            return getTrendingEventsFallback(limit);
        }
    }

    /**
     * Thuật toán Collaborative Filtering dựa trên User-Item Matrix
     */
    private List<String> calculateRecommendationsForUser(String userId, Map<String, Map<String, Double>> matrix) {
        Map<String, Double> targetRatings = matrix.get(userId);
        Map<String, Double> similarityScores = new HashMap<>();

        // 1. Tính toán độ tương đồng Cosine Similarity với tất cả người dùng khác
        matrix.forEach((otherUserId, otherRatings) -> {
            if (!otherUserId.equals(userId)) {
                double similarity = calculateCosineSimilarity(targetRatings, otherRatings);
                if (similarity > 0.1) { // Chỉ giữ lại những người dùng có độ tương đồng dương đáng kể
                    similarityScores.put(otherUserId, similarity);
                }
            }
        });

        // 2. Thu thập và sắp xếp các sự kiện từ những người dùng tương đồng
        // [LỌC GIẢI THUẬT]: Chỉ loại bỏ những sự kiện mà người dùng mục tiêu đã ĐĂNG KÝ (REGISTER = 5.0)
        return similarityScores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(10) // Lấy tối đa 10 người dùng tương đồng nhất
                .flatMap(entry -> matrix.get(entry.getKey()).keySet().stream())
                .filter(eventId -> {
                    // Nếu user đã tương tác với sự kiện này, chỉ loại trừ khi hành vi đó là REGISTER (>= 5.0)
                    boolean isRegistered = targetRatings != null 
                            && targetRatings.containsKey(eventId) 
                            && targetRatings.get(eventId) >= 5.0; // REGISTER có trọng số là 5.0
                    return !isRegistered;
                })
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Công thức tính Cosine Similarity giữa 2 vector thưa (Map)
     */
    private double calculateCosineSimilarity(Map<String, Double> vecA, Map<String, Double> vecB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (String key : vecA.keySet()) {
            dotProduct += vecA.get(key) * vecB.getOrDefault(key, 0.0);
            normA += Math.pow(vecA.get(key), 2);
        }
        for (double val : vecB.values()) {
            normB += Math.pow(val, 2);
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }


    /**
     * Dịch vụ Fallback: Sử dụng danh sách sự kiện xu hướng khi không có gợi ý cá nhân
     */
    private List<EventSearchPublicDTO> getTrendingEventsFallback(int limit) {
        List<EventTrendingResponse> trending = eventInteractionService.getTopTrendingEvents();
        List<EventSearchPublicDTO> dtos = new ArrayList<>();
        String userId = securityUtils.getCurrentUserId();

        List<String> eventIds = trending.stream()
                .limit(limit)
                .map(EventTrendingResponse::getId)
                .collect(Collectors.toList());

        log.info("[RECOMMENDATION] fallback: eventIds = {}", eventIds);
        if (!eventIds.isEmpty()) {
            List<Event> events = eventRepository.findEventsByIdsWithDetails(eventIds);
            Map<String, Event> eventMap = events.stream()
                    .collect(Collectors.toMap(Event::getId, event -> event));
            for (String eventId : eventIds) {
                Event event = eventMap.get(eventId);
                if (event != null 
                        && event.getDeletedAt() == null 
                        && event.getStatus() == EventStatus.PUBLISHED 
                        && event.getEndTime().isAfter(LocalDateTime.now())) {
                    dtos.add(eventMapper.toSearchPublicDTO(event));
                }
            }
        }

        if (userId != null && !dtos.isEmpty()) {
            Set<String> savedEventIds = favoriteRepository.findSavedEventIds(userId, eventIds);
            dtos.forEach(dto -> dto.setIsSaved(savedEventIds.contains(dto.getId())));
        }

        return dtos;
    }
}
