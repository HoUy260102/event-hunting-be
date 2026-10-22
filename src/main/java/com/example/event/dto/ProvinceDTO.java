package com.example.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProvinceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String codeName;
}
