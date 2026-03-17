package com.example.event.controller;

import com.example.event.dto.*;
import com.example.event.dto.request.*;
import com.example.event.dto.response.ApiResponse;
import com.example.event.dto.response.KeysetPageResponse;
import com.example.event.service.EventService;
import com.example.event.service.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;
    private final ShowService showService;

    @PostMapping
    public ResponseEntity<?> createEvent(@Valid @RequestBody CreateEventReq req) {
        eventService.createEvent(req);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{eventId}/shows")
    public ResponseEntity<?> createShow(@Valid @RequestBody CreateShowReq req, @PathVariable String eventId) {
        ShowDTO showDTO = showService.createShow(req, eventId);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message("Thành công.")
                .data(showDTO)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{eventId}/shows/{showId}")
    public ResponseEntity<?> updateShow(@Valid @RequestBody UpdateShowReq req, @PathVariable String eventId, @PathVariable String showId) {
        ShowDTO showDTO = showService.updateShow(req, showId, eventId);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(showDTO)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateEventStatus(@Valid @RequestBody UpdateEventStatusReq req, @PathVariable String id) {
        eventService.updateEventStatus(id, req);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> findEventById(@PathVariable String id) {
        EventDTO eventDTO = eventService.findEventById(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(eventDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/info")
    public ResponseEntity<?> findEventByIdForInfo(@PathVariable String id) {
        EventInfoDTO eventInfoDTO = eventService.findEventInfoById(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(eventInfoDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/overview")
    public ResponseEntity<?> findEventByIdForOverview(@PathVariable String id) {
        EventSummaryDTO eventSummaryDTO = eventService.getEventSummaryById(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(eventSummaryDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/shows")
    public ResponseEntity<?> findAllShowsByEventId(@PathVariable String id) {
        List<ShowDTO> shows = showService.findShowsByEventId(id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(shows)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchEventForAdmin(@Valid EventSearchReq req) {
        Page<EventDTO> events = eventService.getEventSearchForAdmin(req);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(events)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/public/search")
    public ResponseEntity<?> searchEventForPublic(@Valid EventSearchPublicReq req) {
        KeysetPageResponse<EventSearchPublicDTO, String> events = eventService.getEventSearchPublic(req);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(events)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(@RequestBody UpdateEventReq req, @PathVariable String id) {
        EventDTO eventDTO = eventService.updateEvent(req, id);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(eventDTO)
                .message("Thành công.")
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
