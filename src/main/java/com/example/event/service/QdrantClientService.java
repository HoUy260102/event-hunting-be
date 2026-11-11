package com.example.event.service;

import com.example.event.dto.EventSearchPublicDTO;
import com.example.event.dto.request.EventSearchPublicReq;
import com.example.event.dto.response.KeysetPageResponse;
import com.example.event.entity.Event;

public interface QdrantClientService {

    void initCollectionIfNotExists();

    long countPoints();

    void upsertEvent(Event event);

    void deleteEvent(String eventId);

    KeysetPageResponse<EventSearchPublicDTO, String> searchHybrid(EventSearchPublicReq req);
}

