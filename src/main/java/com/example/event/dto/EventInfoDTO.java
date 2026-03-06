package com.example.event.dto;

import com.example.event.constant.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventInfoDTO {
    private String id;
    private String name;
    private String location;
    private Long minPrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private EventStatus status;
    private FileDTO poster;
    private FileDTO banner;
    private ProvinceDTO province;
    private CategoryDTO category;
    private String descriptionHtml;
    private FileDTO organizerLogo;
    private String organizerName;
    private String organizerInfo;
    private List<ShowInfoDTO> shows;
}
