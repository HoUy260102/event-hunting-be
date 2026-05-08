package com.example.event.mapper;

import com.example.event.constant.ShowStatus;
import com.example.event.dto.EventDTO;
import com.example.event.dto.EventInfoDTO;
import com.example.event.dto.EventSearchPublicDTO;
import com.example.event.dto.ShowInfoDTO;
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
        eventDTO.setCategory((event.getCategory() != null && event.getCategory().getDeletedAt() != null)
                ? null
                : categoryMapper.toDTO(event.getCategory()));
        eventDTO.setUserId(event.getUser() != null ? event.getUser().getId() : null);
        return eventDTO;
    }

    public EventSearchPublicDTO toSearchPublicDTO(Event event) {
        EventSearchPublicDTO eventDTO = modelMapper.map(event, EventSearchPublicDTO.class);
        eventDTO.setPoster(fileMapper.toDTO(event.getPoster()));
        eventDTO.setProvince(provinceMapper.toDTO(event.getProvince()));
        eventDTO.setCategory((event.getCategory() != null && event.getCategory().getDeletedAt() != null)
                ? null
                : categoryMapper.toDTO(event.getCategory()));
        return eventDTO;
    }

    public EventInfoDTO toInfoDTO(Event event) {
        EventInfoDTO eventInfoDTO = modelMapper.map(event, EventInfoDTO.class);
        eventInfoDTO.setBanner(fileMapper.toDTO(event.getBanner()));
        eventInfoDTO.setPoster(fileMapper.toDTO(event.getPoster()));
        eventInfoDTO.setOrganizerLogo(fileMapper.toDTO(event.getOrganizerLogo()));
        eventInfoDTO.setProvince(provinceMapper.toDTO(event.getProvince()));
        eventInfoDTO.setCategory((event.getCategory() != null && event.getCategory().getDeletedAt() != null)
                ? null
                : categoryMapper.toDTO(event.getCategory()));
        List<ShowInfoDTO> showInfoDTOS = event.getShows().stream()
                .filter(show -> show.getDeletedAt() == null
                        && show.getStatus() != ShowStatus.DRAFT
                        && show.getStatus() != ShowStatus.DELETED)
                .map(showMapper::toInfoDTO)
                .collect(Collectors.toList());
        eventInfoDTO.setShows(showInfoDTOS);
        return eventInfoDTO;
    }
}
