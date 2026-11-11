package com.example.event.service;

import com.example.event.dto.EventSearchPublicDTO;
import com.example.event.dto.request.EventSearchPublicReq;
import com.example.event.dto.response.KeysetPageResponse;
import com.example.event.entity.Event;

import java.util.List;
import java.util.Map;

public interface QdrantClientService {

    void initCollectionIfNotExists();

    long countPoints();

    void upsertEvent(Event event);

    void deleteEvent(String eventId);

    KeysetPageResponse<EventSearchPublicDTO, String> searchHybrid(EventSearchPublicReq req);

    void initUsersCollectionIfNotExists();

    void upsertUserSparseVectors(List<Map<String, Object>> userPoints);

    List<String> findSimilarUserIds(String userId, int limit);
}

