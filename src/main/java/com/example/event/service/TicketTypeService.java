package com.example.event.service;

import com.example.event.dto.request.CreateTicketTypeReq;
import com.example.event.dto.request.UpdateTicketTypeReq;
import com.example.event.entity.Show;
import com.example.event.entity.TicketType;

import java.util.List;

public interface TicketTypeService {
    List<TicketType> createTicketTypes(List<CreateTicketTypeReq> ticketTypesReq,
                           Show show,
                           String creatorId);
    List<TicketType> updateTicketTypes(List<UpdateTicketTypeReq> ticketTypesReq,
                                       Show show,
                                       String updatorId);
}
