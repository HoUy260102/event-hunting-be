package com.example.event.service.Impl;

import com.example.event.config.QdrantConfig;
import com.example.event.service.GeminiEmbeddingService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiEmbeddingServiceImpl implements GeminiEmbeddingService {

    private final RestClient geminiRestClient;
    private final QdrantConfig qdrantConfig;

    private static final int MAX_TEXT_LENGTH = 4000;
    private static final int TARGET_DIMENSION = 768;

    @Override
    public List<Float> getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        String sanitizedText = text.trim();
        if (sanitizedText.length() > MAX_TEXT_LENGTH) {
            sanitizedText = sanitizedText.substring(0, MAX_TEXT_LENGTH);
        }

        try {
            GeminiEmbedRequest request = GeminiEmbedRequest.builder()
                    .content(Content.builder()
                            .parts(List.of(new Part(sanitizedText)))
                            .build())
                    .outputDimensionality(TARGET_DIMENSION)
                    .build();

            String uri = String.format("/v1beta/models/%s:embedContent?key=%s",
                    qdrantConfig.getGeminiEmbeddingModel(),
                    qdrantConfig.getGeminiApiKey());

            GeminiEmbedResponse response = geminiRestClient.post()
                    .uri(uri)
                    .body(request)
                    .retrieve()
                    .body(GeminiEmbedResponse.class);

            if (response != null && response.getEmbedding() != null && response.getEmbedding().getValues() != null) {
                return response.getEmbedding().getValues();
            }
        } catch (Exception e) {
            log.error("[GEMINI-EMBEDDING] Lỗi khi tạo vector embedding: {}", e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiEmbedRequest {
        private Content content;
        private Integer outputDimensionality;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Part {
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiEmbedResponse {
        private Embedding embedding;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Embedding {
        private List<Float> values;
    }
}

