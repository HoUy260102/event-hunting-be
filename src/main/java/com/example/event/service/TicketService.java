package com.example.event.service;

import com.example.event.dto.TicketDetailDTO;
import com.example.event.dto.TicketSummaryDTO;
import com.example.event.dto.request.SearchTicketPublicReq;
import org.springframework.data.domain.Page;

import java.util.List;

public interface TicketService {
    TicketDetailDTO getTicketDetailById(String id);
    List<TicketSummaryDTO> generateTickets(String reservationId);
    Page<TicketSummaryDTO> getAllMyTickets(SearchTicketPublicReq req);
}

