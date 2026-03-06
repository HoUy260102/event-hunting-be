package com.example.event.service;

import com.example.event.dto.EventDTO;
import com.example.event.dto.EventInfoDTO;
import com.example.event.dto.EventSummaryDTO;
import com.example.event.dto.request.CreateEventReq;
import com.example.event.dto.request.EventSearchReq;
import com.example.event.dto.request.UpdateEventReq;
import com.example.event.dto.request.UpdateEventStatusReq;
import org.springframework.data.domain.Page;

public interface EventService {
    void createEvent(CreateEventReq createEventReq);
    void updateEventStatus(String id, UpdateEventStatusReq req);
    Page<EventDTO> getEventSearchForAdmin(EventSearchReq req);
    EventDTO updateEvent(UpdateEventReq updateEventReq, String id);
    EventDTO findEventById(String id);
    EventInfoDTO findEventInfoById(String id);
    EventSummaryDTO getEventSummaryById(String id);
}
