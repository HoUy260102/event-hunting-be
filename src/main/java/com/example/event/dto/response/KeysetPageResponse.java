package com.example.event.dto.response;

import lombok.*;

import java.util.List;
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Setter
@Getter
public class KeysetPageResponse<T, K> {
    private List<T> content;
    private K nextId;
    private boolean hasNext;
}
