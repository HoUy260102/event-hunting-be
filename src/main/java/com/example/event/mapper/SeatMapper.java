package com.example.event.mapper;

import com.example.event.dto.SeatDTO;
import com.example.event.entity.Seat;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeatMapper {
    private final ModelMapper modelMapper;

    public SeatDTO toDTO(Seat seat) {
        SeatDTO seatDTO = modelMapper.map(seat, SeatDTO.class);
        return seatDTO;
    }
}
