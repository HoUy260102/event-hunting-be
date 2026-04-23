package com.example.event.mapper;

import com.example.event.dto.TicketDTO;
import com.example.event.dto.TicketDetailDTO;
import com.example.event.dto.TicketSummaryDTO;
import com.example.event.entity.Event;
import com.example.event.entity.Reservation;
import com.example.event.entity.Show;
import com.example.event.entity.Ticket;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketMapper {
    private final ModelMapper modelMapper;
    private final FileMapper fileMapper;

    public TicketSummaryDTO toSummaryDTO(Ticket ticket) {
        TicketSummaryDTO ticketDTO = modelMapper.map(ticket, TicketSummaryDTO.class);
        Show show = ticket.getShow();
        Event event = ticket.getEvent();
        Reservation reservation = ticket.getReservation();
        ticketDTO.setEventLocation(event.getLocation());
        ticketDTO.setEventName(event.getName());

        ticketDTO.setShowStartTime(show.getStartTime());
        ticketDTO.setShowEndTime(show.getEndTime());

        ticketDTO.setReservationId(reservation.getId());
        return ticketDTO;
    }

    public TicketDetailDTO toDetailDTO(Ticket ticket) {
        TicketDetailDTO ticketDTO = modelMapper.map(ticket, TicketDetailDTO.class);
        Show show = ticket.getShow();
        Event event = ticket.getEvent();
        Reservation reservation = ticket.getReservation();
        ticketDTO.setEventLocation(event.getLocation());
        ticketDTO.setEventName(event.getName());
        ticketDTO.setEventPoster(fileMapper.toDTO(event.getPoster()));

        ticketDTO.setShowStartTime(show.getStartTime());
        ticketDTO.setShowEndTime(show.getEndTime());

        ticketDTO.setReservationId(reservation.getId());
        ticketDTO.setCustomerName(reservation.getCustomerName());
        ticketDTO.setCustomerEmail(reservation.getCustomerEmail());
        ticketDTO.setCustomerPhone(reservation.getCustomerPhone());

        return ticketDTO;
    }

    public TicketDTO toDTO(Ticket ticket) {
        TicketDTO ticketDTO = modelMapper.map(ticket, TicketDTO.class);
        Reservation reservation = ticket.getReservation();

        ticketDTO.setReservationId(reservation.getId());
        ticketDTO.setCustomerName(reservation.getCustomerName());
        ticketDTO.setCustomerEmail(reservation.getCustomerEmail());
        ticketDTO.setCustomerPhone(reservation.getCustomerPhone());
        return ticketDTO;
    }
}
