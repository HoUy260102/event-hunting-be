package com.example.event.dto.request;

import com.example.event.constant.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseKeysetReq {
    private String nextId;
    private Integer size = 8;
    private String keyword;
    private List<EventStatus> statuses;
}
