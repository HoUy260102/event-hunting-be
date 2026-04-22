package com.example.event.service;

import com.example.event.constant.ShowStatus;
import com.example.event.dto.*;
import com.example.event.dto.request.CreateShowReq;
import com.example.event.dto.request.UpdateShowReq;
import com.example.event.entity.Event;

import java.util.List;

public interface ShowService {
    void createShows(List<CreateShowReq> showsReq,
                     Event event,
                     String creatorId);
    ShowDTO createShow(CreateShowReq showReq, String eventId);
    ShowDTO updateShow(UpdateShowReq showReq, String showId,
                     String eventId);
    ShowRegistryDTO findShowRegistryById(String id);
    ShowBookingDTO findShowBookingById(String id);
    ShowDetailDTO findShowDetailById(String id);
    void updateShowStatus(String showId, ShowStatus status);
    List<ShowDTO> findShowsByEventId(String eventId);
    List<ShowSelectionDTO> findShowSelectionByEventId(String eventId);
}
