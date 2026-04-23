package com.example.event.service;

import com.example.event.dto.*;
import com.example.event.dto.request.*;
import com.example.event.dto.response.KeysetPageResponse;
import org.springframework.data.domain.Page;

public interface EventService {
    void createEvent(CreateEventReq createEventReq);
    void updateEventStatus(String id, UpdateEventStatusReq req);
    Page<EventDTO> getEventSearchForAdmin(EventSearchReq req);
    KeysetPageResponse<EventSearchPublicDTO, String> getEventSearchPublic(EventSearchPublicReq req);
    EventDTO updateEvent(UpdateEventReq updateEventReq, String id);
    EventDTO findEventById(String id);
    EventInfoDTO findEventInfoById(String id);
    EventSummaryDTO getEventSummaryById(String eventId);
    KeysetPageResponse<EventSearchPublicDTO, String> getMyFavoriteEvents(BaseKeysetReq req);
}
