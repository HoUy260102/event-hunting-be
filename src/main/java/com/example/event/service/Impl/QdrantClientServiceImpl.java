package com.example.event.service.Impl;

import com.example.event.config.QdrantConfig;
import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.EventStatus;
import com.example.event.dto.CategoryDTO;
import com.example.event.dto.EventSearchPublicDTO;
import com.example.event.dto.FileDTO;
import com.example.event.dto.ProvinceDTO;
import com.example.event.dto.request.EventSearchPublicReq;
import com.example.event.dto.response.KeysetPageResponse;
import com.example.event.entity.Event;
import com.example.event.repository.FavoriteRepository;
import com.example.event.service.GeminiEmbeddingService;
import com.example.event.service.QdrantClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.ulid.Ulid;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class QdrantClientServiceImpl implements QdrantClientService {

    private final RestClient qdrantRestClient;
    private final QdrantConfig qdrantConfig;
    private final GeminiEmbeddingService geminiEmbeddingService;
    private final FavoriteRepository favoriteRepository;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    private static final ZoneOffset VN_OFFSET = ZoneOffset.ofHours(7);

    @PostConstruct
    public void onStart() {
        try {
            initCollectionIfNotExists();
        } catch (Exception e) {
            log.warn("[QDRANT] Chưa thể kết nối hoặc khởi tạo collection Qdrant khi startup: {}", e.getMessage());
        }
    }

    @Override
    public void initCollectionIfNotExists() {
        String collection = qdrantConfig.getCollectionName();
        try {
            // 1. Kiểm tra collection đã tồn tại chưa
            boolean exists = false;
            try {
                JsonNode res = qdrantRestClient.get()
                        .uri("/collections/{collection}", collection)
                        .retrieve()
                        .body(JsonNode.class);
                if (res != null && "ok".equals(res.path("status").asText())) {
                    exists = true;
                    log.info("[QDRANT] Collection '{}' đã tồn tại.", collection);
                }
            } catch (Exception ex) {
                // Collection chưa tồn tại (404)
                exists = false;
            }

            if (!exists) {
                log.info("[QDRANT] Đang tạo collection '{}' với cấu hình 2-Dense Vector (Gemini AI)...", collection);
                Map<String, Object> body = Map.of(
                        "vectors", Map.of(
                                "core_dense", Map.of("size", 768, "distance", "Cosine"),
                                "desc_dense", Map.of("size", 768, "distance", "Cosine")
                        )
                );

                qdrantRestClient.put()
                        .uri("/collections/{collection}", collection)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("[QDRANT] Đã tạo thành công collection '{}'.", collection);
            }

            // 2. Đảm bảo các payload indexes được tạo
            createPayloadIndexes(collection);

        } catch (Exception e) {
            log.error("[QDRANT] Lỗi khởi tạo collection '{}': {}", collection, e.getMessage(), e);
        }
    }

    @Override
    public long countPoints() {
        String collection = qdrantConfig.getCollectionName();
        try {
            JsonNode res = qdrantRestClient.post()
                    .uri("/collections/{collection}/points/count", collection)
                    .body(Collections.emptyMap())
                    .retrieve()
                    .body(JsonNode.class);
            if (res != null && res.has("result") && res.get("result").has("count")) {
                return res.get("result").get("count").asLong(0L);
            }
        } catch (Exception e) {
            log.warn("[QDRANT] Không thể lấy số lượng point của collection '{}': {}", collection, e.getMessage());
        }
        return 0L;
    }

    private void createPayloadIndexes(String collection) {
        String[] keywords = {"status", "provinceId", "categoryId"};
        for (String field : keywords) {
            createSingleIndex(collection, field, "keyword");
        }
        String[] integers = {"minPrice", "startTime", "endTime"};
        for (String field : integers) {
            createSingleIndex(collection, field, "integer");
        }
    }

    private void createSingleIndex(String collection, String fieldName, String schemaType) {
        try {
            Map<String, Object> body = Map.of(
                    "field_name", fieldName,
                    "field_schema", schemaType
            );
            qdrantRestClient.put()
                    .uri("/collections/{collection}/index", collection)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.debug("[QDRANT] Index '{}' có thể đã tồn tại: {}", fieldName, e.getMessage());
        }
    }

    @Override
    public void upsertEvent(Event event) {
        if (event == null || event.getId() == null) {
            return;
        }

        // Chỉ lưu các sự kiện PUBLISHED vào Qdrant
        if (event.getStatus() != EventStatus.PUBLISHED) {
            log.info("[QDRANT] Event {} không ở trạng thái PUBLISHED ({}), xóa khỏi Qdrant nếu có.",
                    event.getId(), event.getStatus());
            deleteEvent(event.getId());
            return;
        }

        try {
            String uuid = Ulid.from(event.getId()).toUuid().toString();

            // 1. Chuẩn bị text cho 3 vectors
            String categoryName = event.getCategory() != null ? event.getCategory().getName() : "";
            String provinceName = event.getProvince() != null ? event.getProvince().getName() : "";
            String organizerName = event.getOrganizerName() != null ? event.getOrganizerName() : "";
            String eventLocation = event.getLocation() != null ? event.getLocation() : "";

            // Vector 1: core_dense
            String coreText = String.join(" ",
                    Objects.requireNonNullElse(event.getName(), ""),
                    categoryName,
                    organizerName,
                    provinceName,
                    eventLocation
            ).trim();

            // Vector 2: desc_dense
            String descText = event.getDescriptionText() != null ? event.getDescriptionText() : "";

            List<Float> coreVector = geminiEmbeddingService.getEmbedding(coreText);
            List<Float> descVector = geminiEmbeddingService.getEmbedding(descText);

            if (coreVector.isEmpty() && descVector.isEmpty()) {
                log.warn("[QDRANT] Không thể sinh vector cho event {}. Bỏ qua upsert.", event.getId());
                return;
            }

            // Nếu 1 trong 2 vector dense rỗng, fallback mượn vector kia để không lỗi schema Qdrant
            if (coreVector.isEmpty()) coreVector = descVector;
            if (descVector.isEmpty()) descVector = coreVector;

            // 2. Chuẩn bị payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", event.getId());
            payload.put("name", event.getName());
            payload.put("location", event.getLocation());
            payload.put("address", event.getAddress());
            payload.put("status", event.getStatus().name());
            payload.put("minPrice", event.getMinPrice() != null ? event.getMinPrice() : 0L);
            payload.put("organizerName", organizerName);

            if (event.getStartTime() != null) {
                payload.put("startTime", event.getStartTime().toEpochSecond(VN_OFFSET));
            }
            if (event.getEndTime() != null) {
                payload.put("endTime", event.getEndTime().toEpochSecond(VN_OFFSET));
            }
            if (event.getProvince() != null) {
                payload.put("provinceId", event.getProvince().getId());
                payload.put("provinceName", event.getProvince().getName());
            }
            if (event.getCategory() != null) {
                payload.put("categoryId", event.getCategory().getId());
                payload.put("categoryName", event.getCategory().getName());
            }
            if (event.getPoster() != null) {
                payload.put("posterId", event.getPoster().getId());
                payload.put("posterUrl", event.getPoster().getUrl());
            }

            // 3. Chuẩn bị vector data (2-Dense Vector thuần Gemini AI)
            Map<String, Object> vectors = new HashMap<>();
            vectors.put("core_dense", coreVector);
            vectors.put("desc_dense", descVector);

            Map<String, Object> point = Map.of(
                    "id", uuid,
                    "vector", vectors,
                    "payload", payload
            );

            Map<String, Object> requestBody = Map.of("points", List.of(point));

            String collection = qdrantConfig.getCollectionName();
            qdrantRestClient.put()
                    .uri("/collections/{collection}/points?wait=true", collection)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[QDRANT] Đã upsert point thành công cho eventId={} (uuid={})", event.getId(), uuid);

        } catch (Exception e) {
            log.error("[QDRANT] Lỗi upsert event {}: {}", event.getId(), e.getMessage(), e);
            throw new RuntimeException("Lỗi đồng bộ Qdrant", e);
        }
    }

    @Override
    public void deleteEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }

        try {
            String uuid = Ulid.from(eventId).toUuid().toString();
            String collection = qdrantConfig.getCollectionName();

            Map<String, Object> requestBody = Map.of("points", List.of(uuid));

            qdrantRestClient.post()
                    .uri("/collections/{collection}/points/delete?wait=true", collection)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[QDRANT] Đã xóa point thành công cho eventId={} (uuid={})", eventId, uuid);

        } catch (Exception e) {
            log.error("[QDRANT] Lỗi xóa event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Lỗi xóa point Qdrant", e);
        }
    }

    @Override
    public KeysetPageResponse<EventSearchPublicDTO, String> searchHybrid(EventSearchPublicReq req) {
        int pageSize = (req.getSize() != null && req.getSize() > 0) ? req.getSize() : 8;
        int fetchLimit = pageSize + 1; // Lấy dư 1 để xác định hasNext

        // Parse offset từ nextId (nếu có, dạng số offset integer: "0", "8", "16"...)
        int offset = 0;
        if (req.getNextId() != null && !req.getNextId().isBlank()) {
            try {
                offset = Integer.parseInt(req.getNextId().trim());
            } catch (NumberFormatException ignored) {
                offset = 0;
            }
        }

        // 1. Xây dựng bộ lọc payload (Must match Filter)
        List<Map<String, Object>> mustFilters = new ArrayList<>();
        mustFilters.add(Map.of("key", "status", "match", Map.of("value", EventStatus.PUBLISHED.name())));

        if (req.getProvinceId() != null && !req.getProvinceId().isBlank()) {
            mustFilters.add(Map.of("key", "provinceId", "match", Map.of("value", req.getProvinceId().trim())));
        }

        if (req.getCategoryIds() != null && !req.getCategoryIds().isEmpty()) {
            mustFilters.add(Map.of("key", "categoryId", "match", Map.of("any", req.getCategoryIds())));
        }

        if (req.getMinPrice() != null && req.getMinPrice() > 0) {
            mustFilters.add(Map.of("key", "minPrice", "range", Map.of("gte", req.getMinPrice())));
        }

        // Time Overlap: startTime <= req.endTime AND endTime >= req.startTime
        if (req.getStartTime() != null && req.getEndTime() != null) {
            long reqStartSec = req.getStartTime().toEpochSecond(VN_OFFSET);
            long reqEndSec = req.getEndTime().toEpochSecond(VN_OFFSET);
            mustFilters.add(Map.of("key", "startTime", "range", Map.of("lte", reqEndSec)));
            mustFilters.add(Map.of("key", "endTime", "range", Map.of("gte", reqStartSec)));
        } else if (req.getStartTime() != null) {
            long reqStartSec = req.getStartTime().toEpochSecond(VN_OFFSET);
            mustFilters.add(Map.of("key", "endTime", "range", Map.of("gte", reqStartSec)));
        } else if (req.getEndTime() != null) {
            long reqEndSec = req.getEndTime().toEpochSecond(VN_OFFSET);
            mustFilters.add(Map.of("key", "startTime", "range", Map.of("lte", reqEndSec)));
        }

        Map<String, Object> filter = Map.of("must", mustFilters);
        String collection = qdrantConfig.getCollectionName();

        List<JsonNode> pointNodes = new ArrayList<>();

        try {
            boolean hasKeyword = req.getKeyword() != null && !req.getKeyword().trim().isEmpty();

            if (hasKeyword) {
                // === DENSE VECTOR SEARCH (GEMINI) VỚI FORMULA QUERY FUSION (70% Core + 30% Desc) ===
                String keyword = req.getKeyword().trim();
                List<Float> queryDense = geminiEmbeddingService.getEmbedding(keyword);

                List<Map<String, Object>> prefetchList = new ArrayList<>();
                // Prefetch limit động theo offset + fetchLimit để đảm bảo phân trang sâu không bị hụt kết quả
                int prefetchLimit = Math.max(100, offset + fetchLimit + 20);

                if (!queryDense.isEmpty()) {
                    prefetchList.add(Map.of(
                            "query", queryDense,
                            "using", "core_dense",
                            "limit", prefetchLimit
                    ));
                    prefetchList.add(Map.of(
                            "query", queryDense,
                            "using", "desc_dense",
                            "limit", prefetchLimit
                    ));
                }

                Map<String, Object> queryBody = new HashMap<>();
                queryBody.put("prefetch", prefetchList);
                // Qdrant Formula Query: Điểm = 0.8 * score(core) + 0.2 * score(desc)
                queryBody.put("query", Map.of(
                        "formula", Map.of(
                                "sum", List.of(
                                        Map.of("mult", List.of(0.8, "$score[0]")),
                                        Map.of("mult", List.of(0.2, "$score[1]"))
                                )
                        )
                ));
                // Ngưỡng điểm tương đồng động (Dynamic Threshold) theo độ dài và ngữ cảnh từ khóa:
                // - Từ đơn rất ngắn (<= 1 từ hoặc <= 8 ký tự như "music", "kịch"): ngưỡng 0.45f
                // - Từ ghép 2 chữ tiếng Việt (<= 2 từ hoặc <= 14 ký tự như "âm nhạc", "ca nhạc", "vẽ tranh"): ngưỡng 0.52f
                // - Cụm từ cụ thể / tên riêng (>= 3 từ như "phùng khánh linh"): ngưỡng 0.55f
                float threshold = 0.55f;
                String[] words = keyword.split("\\s+");
                if (words.length <= 1 && keyword.length() <= 8) {
                    threshold = 0.45f;
                } else if (words.length <= 2 && keyword.length() <= 14) {
                    threshold = 0.52f;
                }
                queryBody.put("score_threshold", threshold);
                queryBody.put("filter", filter);
                queryBody.put("limit", fetchLimit);
                queryBody.put("offset", offset);
                queryBody.put("with_payload", true);

                JsonNode response = qdrantRestClient.post()
                        .uri("/collections/{collection}/points/query", collection)
                        .body(queryBody)
                        .retrieve()
                        .body(JsonNode.class);

                if (response != null && response.has("result") && response.get("result").has("points")) {
                    response.get("result").get("points").forEach(pointNodes::add);
                }

            } else {
                // === KHÔNG CÓ TỪ KHÓA -> QUERY / FILTER THƯỜNG VỚI INTEGER OFFSET ===
                Map<String, Object> queryNoKeyword = new HashMap<>();
                queryNoKeyword.put("filter", filter);
                queryNoKeyword.put("limit", fetchLimit);
                queryNoKeyword.put("offset", offset);
                queryNoKeyword.put("with_payload", true);

                JsonNode response = qdrantRestClient.post()
                        .uri("/collections/{collection}/points/query", collection)
                        .body(queryNoKeyword)
                        .retrieve()
                        .body(JsonNode.class);

                if (response != null && response.has("result") && response.get("result").has("points")) {
                    response.get("result").get("points").forEach(pointNodes::add);
                }
            }

        } catch (Exception e) {
            log.error("[QDRANT] Lỗi tìm kiếm Hybrid Search: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi tìm kiếm Hybrid", e);
        }

        // 2. Chuyển đổi payload thành EventSearchPublicDTO
        boolean hasNext = pointNodes.size() > pageSize;
        int sliceLimit = Math.min(pointNodes.size(), pageSize);

        List<EventSearchPublicDTO> dtos = new ArrayList<>(sliceLimit);
        for (int i = 0; i < sliceLimit; i++) {
            JsonNode payloadNode = pointNodes.get(i).get("payload");
            if (payloadNode != null) {
                dtos.add(mapPayloadToDTO(payloadNode));
            }
        }

        // 3. Map isSaved nếu người dùng đã đăng nhập
        String currentUserId = securityUtils.getCurrentUserId();
        if (currentUserId != null && !dtos.isEmpty()) {
            List<String> eventIds = dtos.stream().map(EventSearchPublicDTO::getId).toList();
            Set<String> savedEventIds = favoriteRepository.findSavedEventIds(currentUserId, eventIds);
            dtos.forEach(dto -> dto.setIsSaved(savedEventIds.contains(dto.getId())));
        } else {
            dtos.forEach(dto -> dto.setIsSaved(false));
        }

        String nextIdStr = hasNext ? String.valueOf(offset + pageSize) : null;

        return KeysetPageResponse.<EventSearchPublicDTO, String>builder()
                .content(dtos)
                .hasNext(hasNext)
                .nextId(nextIdStr)
                .build();
    }

    private EventSearchPublicDTO mapPayloadToDTO(JsonNode payload) {
        EventSearchPublicDTO.EventSearchPublicDTOBuilder builder = EventSearchPublicDTO.builder()
                .id(payload.path("id").asText(null))
                .name(payload.path("name").asText(null))
                .location(payload.path("location").asText(null))
                .address(payload.path("address").asText(null))
                .minPrice(payload.path("minPrice").asLong(0L));

        String statusStr = payload.path("status").asText(null);
        if (statusStr != null) {
            try {
                builder.status(EventStatus.valueOf(statusStr));
            } catch (Exception ignored) {}
        }

        if (payload.hasNonNull("startTime")) {
            long startSec = payload.get("startTime").asLong();
            builder.startTime(LocalDateTime.ofEpochSecond(startSec, 0, VN_OFFSET));
        }

        if (payload.hasNonNull("endTime")) {
            long endSec = payload.get("endTime").asLong();
            builder.endTime(LocalDateTime.ofEpochSecond(endSec, 0, VN_OFFSET));
        }

        if (payload.hasNonNull("provinceId")) {
            builder.province(ProvinceDTO.builder()
                    .id(payload.path("provinceId").asText())
                    .name(payload.path("provinceName").asText(null))
                    .build());
        }

        if (payload.hasNonNull("categoryId")) {
            builder.category(CategoryDTO.builder()
                    .id(payload.path("categoryId").asText())
                    .name(payload.path("categoryName").asText(null))
                    .build());
        }

        if (payload.hasNonNull("posterId") || payload.hasNonNull("posterUrl")) {
            builder.poster(FileDTO.builder()
                    .id(payload.path("posterId").asText(null))
                    .url(payload.path("posterUrl").asText(null))
                    .build());
        }

        return builder.build();
    }
}
