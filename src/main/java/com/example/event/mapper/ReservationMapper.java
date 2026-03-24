package com.example.event.mapper;

import com.example.event.dto.ReservationDTO;
import com.example.event.dto.ReservationDetailDTO;
import com.example.event.dto.ReservationItemDTO;
import com.example.event.entity.Event;
import com.example.event.entity.Reservation;
import com.example.event.entity.ReservationItem;
import com.example.event.entity.Show;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReservationMapper {
    private final ModelMapper modelMapper;
    private final ReservationItemMapper reservationItemMapper;

    public ReservationDTO toDto(Reservation reservation) {
        ReservationDTO reservationDTO = modelMapper.map(reservation, ReservationDTO.class);
        reservationDTO.setUserId(reservation.getUser() != null ? reservation.getUser().getId() : null);
        reservationDTO.setItems(reservation.getItems()
                .stream()
                .collect(Collectors.groupingBy(
                        item -> item.getTicketType().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    ReservationItem first = list.get(0);
                                    return ReservationItemDTO.builder()
                                            .ticketTypeId(first.getTicketType().getId())
                                            .ticketTypeName(first.getTicketType().getName())
                                            .quantity(list.stream().mapToInt(ReservationItem::getQuantity).sum())
                                            .totalPrice(list.stream().mapToLong(ReservationItem::getTotalPrice).sum())
                                            .unitPrice(first.getUnitPrice())
                                            .seatId(null)
                                            .seatCode(null)
                                            .seatDisplayName(null)
                                            .build();
                                }
                        )
                ))
                .values()
                .stream()
                .collect(Collectors.toList()));
        return reservationDTO;
    }

    public ReservationDetailDTO toDetailDto(Reservation reservation) {
        ReservationDetailDTO reservationDTO = modelMapper.map(reservation, ReservationDetailDTO.class);
        Show show = reservation.getShow();
        Event event = reservation.getEvent();
        reservationDTO.setShowStartTime(show.getStartTime());
        reservationDTO.setShowEndTime(show.getEndTime());
        reservationDTO.setEventName(event.getName());
        reservationDTO.setEventLocation(event.getLocation());
        reservationDTO.setItems(reservation.getItems()
                .stream()
                .map(reservationItemMapper::toDto)
                .collect(Collectors.toList()));
        return reservationDTO;
    }
}
