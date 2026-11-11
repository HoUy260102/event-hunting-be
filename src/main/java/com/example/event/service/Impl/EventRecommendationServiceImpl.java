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
import com.example.event.service.QdrantClientService;
import com.example.event.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.Ulid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final QdrantClientService qdrantClientService;

    private static final String REC_KEY_PREFIX = "user:recommendation:";
    private static final long REC_TTL_SECONDS = 86400L; // 1 ngày

    @Value("${app.recommendation.days-window:180}")
    private int defaultDaysWindow;

    @Override
    @Transactional(readOnly = true)
    public void runRecommendationJob(int days) {
        log.info("[RECOMMENDATION] Bắt đầu đồng bộ Vector người dùng và tính gợi ý qua Qdrant Sparse Vector ({} ngày qua).", days);
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(days);

        // 1. Lấy danh sách tất cả các User ID có tương tác trong N ngày qua
        List<String> activeUserIds = interactionRepository.findActiveUserIds(sinceDate);
        if (activeUserIds.isEmpty()) {
            log.warn("[RECOMMENDATION] Không tìm thấy người dùng nào có tương tác trong {} ngày qua.", days);
            return;
        }

        log.info("[RECOMMENDATION] Tìm thấy {} người dùng hoạt động. Bắt đầu xử lý theo từng mẻ (Batch 50 users)...", activeUserIds.size());
        int batchSize = 50;
        int totalUsers = activeUserIds.size();

        for (int i = 0; i < totalUsers; i += batchSize) {
            int end = Math.min(i + batchSize, totalUsers);
            List<String> batchUserIds = activeUserIds.subList(i, end);

            try {
                // A. Lấy tương tác của mẻ này theo sequence_id
                List<Object[]> rows = interactionRepository.findUserInteractionsByUsers(batchUserIds, sinceDate);

                // B. Gom nhóm Sparse Vector cho từng User
                Map<String, Map<Long, Double>> userInteractions = new HashMap<>();
                for (Object[] row : rows) {
                    String uid = (String) row[0];
                    Long seqId = ((Number) row[1]).longValue();
                    Double weight = ((Number) row[2]).doubleValue();
                    userInteractions.computeIfAbsent(uid, k -> new HashMap<>()).put(seqId, weight);
                }

                // C. Chuẩn bị points để đẩy lên Qdrant users_cf
                List<Map<String, Object>> qdrantPoints = new ArrayList<>();
                for (Map.Entry<String, Map<Long, Double>> entry : userInteractions.entrySet()) {
                    String uid = entry.getKey();
                    Map<Long, Double> seqWeights = entry.getValue();

                    List<Long> indices = new ArrayList<>(seqWeights.keySet());
                    List<Double> rawValues = new ArrayList<>(seqWeights.values());

                    // Chuẩn hóa L2-norm để đưa Tích vô hướng về chuẩn Cosine Similarity [0.0 -> 1.0]
                    double sumSq = 0.0;
                    for (Double val : rawValues) {
                        sumSq += val * val;
                    }
                    double norm = Math.sqrt(sumSq);

                    List<Double> normalizedValues = new ArrayList<>(rawValues.size());
                    for (Double val : rawValues) {
                        normalizedValues.add(norm > 0 ? (val / norm) : val);
                    }

                    String pointId;
                    try {
                        pointId = Ulid.from(uid).toUuid().toString();
                    } catch (Exception e) {
                        pointId = uid;
                    }

                    qdrantPoints.add(Map.of(
                            "id", pointId,
                            "vector", Map.of(
                                    "user_sparse", Map.of(
                                            "indices", indices,
                                            "values", normalizedValues
                                    )
                            ),
                            "payload", Map.of("userId", uid)
                    ));
                }

                // D. Upsert mẻ này vào Qdrant
                qdrantClientService.upsertUserSparseVectors(qdrantPoints);

                log.info("[RECOMMENDATION] Hoàn thành đồng bộ mẻ User [{}/{}].", end, totalUsers);

                // Nghỉ 50ms giữa các mẻ để nhường tài nguyên CPU
                Thread.sleep(50);

            } catch (Exception e) {
                log.error("[RECOMMENDATION] Lỗi khi xử lý mẻ User từ {} đến {}: {}", i, end, e.getMessage(), e);
            }
        }
        log.info("[RECOMMENDATION] Hoàn thành việc đồng bộ toàn bộ Vector người dùng lên Qdrant.");
    }

    /**
     * Thuật toán cũ tính ma trận hoàn toàn trên RAM của Java (Legacy).
     * Được giữ lại 100% làm phương án đối chứng / dự phòng.
     */
    
    @Override
    @Transactional(readOnly = true)
    public void runRecommendationJobLegacy(int days) {
        log.info("[RECOMMENDATION-LEGACY] Bắt đầu tính toán ma trận gợi ý trên RAM Java ({} ngày qua).", days);
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(days);
        
        // 1. Lấy dữ liệu tương tác đã được gom nhóm và tính trọng số lớn nhất từ Database
        List<Object[]> aggregatedInteractions = interactionRepository.findAggregatedInteractions(sinceDate);
        if (aggregatedInteractions.isEmpty()) {
            log.warn("[RECOMMENDATION-LEGACY] Không tìm thấy dữ liệu tương tác nào trong vòng {} ngày qua. Huỷ bỏ job.", days);
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
                
                log.debug("[RECOMMENDATION-LEGACY] Đã lưu gợi ý cho User {}: {}", userId, recommendedEventIds);
            } catch (Exception e) {
                log.error("[RECOMMENDATION-LEGACY] Lỗi khi xử lý gợi ý cho User {}: {}", userId, e.getMessage());
            }
        }
        log.info("[RECOMMENDATION-LEGACY] Hoàn thành việc cập nhật toàn bộ gợi ý cá nhân hóa lên Redis bằng RAM.");
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
        List<String> candidateEventIds = null;

        if (cachedJson != null && !cachedJson.isEmpty()) {
            try {
                candidateEventIds = objectMapper.readValue(cachedJson, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("[RECOMMENDATION] Lỗi giải mã cache gợi ý của User {}: {}", userId, e.getMessage());
            }
        }

        // Cache miss -> Truy vấn láng giềng tương đồng qua Qdrant Sparse Vector theo thời gian thực
        if (candidateEventIds == null || candidateEventIds.isEmpty()) {
            log.info("[RECOMMENDATION] Cache miss gợi ý cho User {}. Đang truy vấn láng giềng từ Qdrant.", userId);
            List<String> similarUserIds = qdrantClientService.findSimilarUserIds(userId, 10);

            if (similarUserIds.isEmpty()) {
                log.info("[RECOMMENDATION] Không tìm thấy láng giềng tương đồng cho User {}. Fallback sang xu hướng.", userId);
                return getTrendingEventsFallback(limit);
            }

            LocalDateTime sinceDate = LocalDateTime.now().minusDays(defaultDaysWindow);
            List<Object[]> candidateRows = interactionRepository.findCandidateEventIdsWithScore(similarUserIds, sinceDate);

            // [LỌC THỰC TẾ] Lấy các sự kiện mà người dùng CHƯA đăng ký mua vé (Real-time check)
            List<EventInteraction> registrations = interactionRepository.findByUser_IdAndType(userId, InteractionType.REGISTER);
            Set<String> registeredEventIds = registrations.stream()
                    .map(ri -> ri.getEvent().getId())
                    .collect(Collectors.toSet());

            candidateEventIds = candidateRows.stream()
                    .map(r -> (String) r[0])
                    .filter(eventId -> !registeredEventIds.contains(eventId))
                    .limit(10)
                    .collect(Collectors.toList());

            if (candidateEventIds.isEmpty()) {
                return getTrendingEventsFallback(limit);
            }

            // Lưu danh sách ID sự kiện gợi ý vào Redis (TTL 24h)
            try {
                String jsonVal = objectMapper.writeValueAsString(candidateEventIds);
                redisService.set(redisKey, jsonVal, REC_TTL_SECONDS);
            } catch (Exception e) {
                log.error("[RECOMMENDATION] Lỗi lưu cache gợi ý cho User {}: {}", userId, e.getMessage());
            }
        }

        // [LỌC THỰC TẾ TRƯỚC KHI TRẢ VỀ] Lấy các sự kiện mà người dùng CHƯA đăng ký mua vé
        List<EventInteraction> registrations = interactionRepository.findByUser_IdAndType(userId, InteractionType.REGISTER);
        Set<String> registeredEventIds = registrations.stream()
                .map(ri -> ri.getEvent().getId())
                .collect(Collectors.toSet());

        List<String> finalRecommendedIds = candidateEventIds.stream()
                .filter(eventId -> !registeredEventIds.contains(eventId))
                .limit(limit)
                .collect(Collectors.toList());

        if (finalRecommendedIds.isEmpty()) {
            return getTrendingEventsFallback(limit);
        }

        // Lấy thông tin chi tiết các sự kiện gợi ý từ Database (Batch fetch để tránh N+1)
        List<EventSearchPublicDTO> dtos = new ArrayList<>();
        List<Event> events = eventRepository.findEventsByIdsWithDetails(finalRecommendedIds);
        Map<String, Event> eventMap = events.stream()
                .collect(Collectors.toMap(Event::getId, event -> event));
        for (String eventId : finalRecommendedIds) {
            Event event = eventMap.get(eventId);
            if (event != null 
                    && event.getDeletedAt() == null 
                    && event.getStatus() == EventStatus.PUBLISHED 
                    && event.getEndTime() != null 
                    && event.getEndTime().isAfter(LocalDateTime.now())) {
                dtos.add(eventMapper.toSearchPublicDTO(event));
            }
        }

        if (dtos.isEmpty()) {
            return getTrendingEventsFallback(limit);
        }

        // Map trạng thái IsSaved của người dùng vào mỗi sự kiện
        List<String> eventIds = dtos.stream().map(EventSearchPublicDTO::getId).collect(Collectors.toList());
        Set<String> savedEventIds = favoriteRepository.findSavedEventIds(userId, eventIds);
        dtos.forEach(dto -> dto.setIsSaved(savedEventIds.contains(dto.getId())));

        return dtos;
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
