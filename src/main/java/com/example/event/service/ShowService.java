package com.example.event.service;

import com.example.event.constant.ShowStatus;
import com.example.event.dto.ShowDTO;
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
    void updateShowStatus(String showId, ShowStatus status);
    List<ShowDTO> findShowsByEventId(String eventId);
}
