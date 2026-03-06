package com.example.event.mapper;

import com.example.event.dto.ProvinceDTO;
import com.example.event.entity.Province;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProvinceMapper {
    private final ModelMapper modelMapper;
    public ProvinceDTO toDTO(Province province) {
        ProvinceDTO provinceDTO = modelMapper.map(province, ProvinceDTO.class);
        return provinceDTO;
    }
}
