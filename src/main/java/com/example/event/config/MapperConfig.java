package com.example.event.config;

import com.example.event.dto.ReservationDetailDTO;
import com.example.event.entity.Reservation;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfig {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT);
        modelMapper.typeMap(Reservation.class, ReservationDetailDTO.class)
                .addMappings(m -> {
                    m.skip(ReservationDetailDTO::setItems);
                });
        return modelMapper;
    }
}
