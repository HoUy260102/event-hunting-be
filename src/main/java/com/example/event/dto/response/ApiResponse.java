package com.example.event.dto.response;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Setter
@Getter
public class ApiResponse<T> {
    private int status;
    private String message;
    private T data;
}
