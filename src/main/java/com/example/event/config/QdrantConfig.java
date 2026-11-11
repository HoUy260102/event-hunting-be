package com.example.event.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Getter
@Configuration
public class QdrantConfig {

    @Value("${qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    @Value("${qdrant.api-key:}")
    private String qdrantApiKey;

    @Value("${qdrant.collection-name:events}")
    private String collectionName;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.embedding-model:gemini-embedding-001}")
    private String geminiEmbeddingModel;

    @Bean
    public RestClient qdrantRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(qdrantUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (qdrantApiKey != null && !qdrantApiKey.isBlank()) {
            builder.defaultHeader("api-key", qdrantApiKey.trim());
        }

        return builder.build();
    }

    @Bean
    public RestClient geminiRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

