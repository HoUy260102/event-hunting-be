package com.example.event.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseKeysetReq {
    private String nextId;
    private Integer size = 8;
    private String keyword;
}
