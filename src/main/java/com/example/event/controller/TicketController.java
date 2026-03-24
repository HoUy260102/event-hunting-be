package com.example.event.controller;

import com.example.event.dto.TicketDetailDTO;
import com.example.event.dto.TicketSummaryDTO;
import com.example.event.dto.request.SearchTicketPublicReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;

    @GetMapping("/my-tickets")
    public ResponseEntity<?> findMyTickets(SearchTicketPublicReq req) {
        Page<TicketSummaryDTO> ticketDTOS = ticketService.getAllMyTickets(req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(ticketDTOS)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    @GetMapping("/my-tickets/{id}")
    public ResponseEntity<?> findTicketDetailById(@PathVariable String id) {
        TicketDetailDTO ticketDTOS = ticketService.getTicketDetailById(id);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(ticketDTOS)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}
