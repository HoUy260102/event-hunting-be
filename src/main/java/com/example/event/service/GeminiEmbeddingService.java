package com.example.event.service;

import java.util.List;

public interface GeminiEmbeddingService {
    /**
     * Sinh vector embedding 768 chiều cho văn bản đầu vào.
     * Trả về danh sách rỗng nếu text null/rỗng hoặc API gặp sự cố.
     */
    List<Float> getEmbedding(String text);
}

