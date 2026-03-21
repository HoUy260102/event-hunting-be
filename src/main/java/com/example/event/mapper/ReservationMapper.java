package com.example.event.mapper;

import com.example.event.dto.ReservationDTO;
import com.example.event.entity.Reservation;
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
                .map(reservationItemMapper::toDto)
                .collect(Collectors.toList()));
        return reservationDTO;
    }
}
