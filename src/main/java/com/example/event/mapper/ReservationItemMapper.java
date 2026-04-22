package com.example.event.mapper;

import com.example.event.dto.ReservationItemDTO;
import com.example.event.entity.ReservationItem;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationItemMapper {
    private final ModelMapper modelMapper;
    public ReservationItemDTO toDto(ReservationItem reservationItem) {
        ReservationItemDTO reservationItemDTO = modelMapper.map(reservationItem, ReservationItemDTO.class);
        reservationItemDTO.setTicketTypeId(reservationItem.getTicketType() != null ? reservationItem.getTicketType().getId() : null);
        reservationItemDTO.setTicketTierId(reservationItem.getTicketTier() != null ? reservationItem.getTicketTier().getId() : null);
        reservationItemDTO.setSeatId(reservationItem.getSeat() != null ? reservationItem.getSeat().getId() : null);
        reservationItemDTO.setSeatDisplayName(reservationItem.getSeat() != null ? reservationItem.getSeat().getRowName() + "-" +reservationItem.getSeat().getSeatNumber() : null);
        reservationItemDTO.setDiscountAmount(reservationItem.getDiscountAmount() != null ? reservationItem.getDiscountAmount() : 0L);
        reservationItemDTO.setFinalPrice(reservationItem.getFinalPrice() != null ? reservationItem.getFinalPrice() : reservationItem.getTotalPrice());
        return reservationItemDTO;
    }
}
