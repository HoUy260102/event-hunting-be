package com.example.event.service;

import com.example.event.constant.InteractionType;
import com.example.event.dto.response.EventTrendingResponse;

import java.util.List;

public interface EventInteractionService {
    void addInteraction(String eventId, String userId, InteractionType type);
    List<EventTrendingResponse> getTopTrendingEvents();
}
