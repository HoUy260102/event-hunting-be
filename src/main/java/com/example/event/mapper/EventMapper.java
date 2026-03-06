package com.example.event.mapper;

import com.example.event.dto.EventDTO;
import com.example.event.dto.EventInfoDTO;
import com.example.event.dto.EventSummaryDTO;
import com.example.event.dto.ShowSummaryDTO;
import com.example.event.entity.Event;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventMapper {
    private final ModelMapper modelMapper;
    private final FileMapper fileMapper;
    private final ProvinceMapper provinceMapper;
    private final CategoryMapper categoryMapper;
    private final ShowMapper showMapper;

    public EventDTO toDTO(Event event) {
        EventDTO eventDTO = modelMapper.map(event, EventDTO.class);
        eventDTO.setBanner(fileMapper.toDTO(event.getBanner()));
        eventDTO.setPoster(fileMapper.toDTO(event.getPoster()));
        eventDTO.setOrganizerLogo(fileMapper.toDTO(event.getOrganizerLogo()));
        eventDTO.setProvince(provinceMapper.toDTO(event.getProvince()));
        eventDTO.setCategory(categoryMapper.toDTO(event.getCategory()));
        return eventDTO;
    }

    public EventInfoDTO toInfoDTO(Event event) {
        EventInfoDTO eventInfoDTO = modelMapper.map(event, EventInfoDTO.class);
        eventInfoDTO.setBanner(fileMapper.toDTO(event.getBanner()));
        eventInfoDTO.setPoster(fileMapper.toDTO(event.getPoster()));
        eventInfoDTO.setOrganizerLogo(fileMapper.toDTO(event.getOrganizerLogo()));
        eventInfoDTO.setProvince(provinceMapper.toDTO(event.getProvince()));
        eventInfoDTO.setCategory(categoryMapper.toDTO(event.getCategory()));
        eventInfoDTO.setShows(event.getShows().stream()
                .map(showMapper::toInfoDTO)
                .collect(Collectors.toList()));
        return eventInfoDTO;
    }

    public EventSummaryDTO toSummaryDTO(Event event) {
        EventSummaryDTO eventSummaryDTO = EventSummaryDTO.builder()
                .id(event.getId())
                .startDate(event.getStartTime())
                .endDate(event.getEndTime())
                .location(event.getLocation())
                .name(event.getName())
                .status(event.getStatus())
                .build();
        eventSummaryDTO.setPoster(fileMapper.toDTO(event.getPoster()));
        List<ShowSummaryDTO> showSummaryDTOS = event.getShows()
                .stream()
                .filter(show -> show.getDeletedAt() == null)
                .map(showMapper::toSummaryDTO)
                .collect(Collectors.toList());
        eventSummaryDTO.setShows(showSummaryDTOS);
        //TODO: nhớ chỉ lấy show active thôi nha
        eventSummaryDTO.setTotalQuantity(showSummaryDTOS
                .stream()
                .mapToInt(ShowSummaryDTO::getTotalQuantity)
                .sum());
        eventSummaryDTO.setSoldQuantity(showSummaryDTOS
                .stream()
                .mapToInt(ShowSummaryDTO::getSoldQuantity)
                .sum());
        eventSummaryDTO.setTotalRevenue(showSummaryDTOS
                .stream()
                .mapToLong(ShowSummaryDTO::getTotalRevenue)
                .sum());
        return eventSummaryDTO;
    }
}
