package com.example.event.service;

public interface QdrantDataMigrationService {

    /**
     * Đồng bộ toàn bộ các sự kiện PUBLISHED từ MySQL sang Qdrant.
     * @return Số lượng sự kiện đã index thành công.
     */
    int reindexAllPublishedEvents();
}

