package com.example.event.dto;

import com.example.event.constant.EventStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventSearchPublicDTO {
    private String id;
    private String name;

    private String location;
    @Enumerated(EnumType.STRING)
    private EventStatus status;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;
    private Long minPrice;
    private Boolean isSaved;

    private ProvinceDTO province;
    private CategoryDTO category;

    private FileDTO poster;
}
