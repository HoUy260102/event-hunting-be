package com.example.event.dto;

import com.example.event.constant.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventDTO {
    private String id;
    private String name;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private EventStatus status;
    private FileDTO poster;
    private FileDTO banner;
    private ProvinceDTO province;
    private CategoryDTO category;
    private String descriptionHtml;
    private String descriptionText;
    private FileDTO organizerLogo;
    private String organizerName;
    private String organizerInfo;
}
