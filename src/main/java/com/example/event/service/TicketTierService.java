package com.example.event.service;

import com.example.event.dto.request.CreateTicketTierReq;
import com.example.event.dto.request.UpdateTicketTierReq;
import com.example.event.entity.TicketTier;
import com.example.event.entity.TicketType;

import java.util.List;

public interface TicketTierService {
    List<TicketTier> createTicketTiers(List<CreateTicketTierReq> ticketTiersReq,
                           TicketType ticketType,
                           String creatorId);
    List<TicketTier> updateTicketTiers(List<UpdateTicketTierReq> ticketTiersReq,
                                       TicketType ticketType,
                                       String updatorId);
}
