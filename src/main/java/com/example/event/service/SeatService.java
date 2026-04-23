package com.example.event.service;

import com.example.event.dto.request.CreateSeatReq;
import com.example.event.dto.request.UpdateSeatReq;
import com.example.event.entity.Seat;
import com.example.event.entity.TicketType;

import java.util.List;

public interface SeatService {
    List<Seat> createSeats(List<CreateSeatReq> seatsReq,
                                 TicketType ticketType,
                                 String creatorId);
    List<Seat> createUnassignedSeats(TicketType ticketType,
                                     String creatorId);
    List<Seat> updateAssignedSeats(List<UpdateSeatReq> seatsReq,
                                       TicketType ticketType,
                                       String updatorId);
    List<Seat> updateUnassignedSeats(TicketType ticketType, int newQuantity, String updatorId);
}
