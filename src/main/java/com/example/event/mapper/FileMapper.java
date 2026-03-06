package com.example.event.mapper;

import com.example.event.dto.FileDTO;
import com.example.event.entity.File;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileMapper {
    private final ModelMapper modelMapper;
    public FileDTO toDTO(File file) {
        if (file == null) return null;
        FileDTO fileDTO = modelMapper.map(file, FileDTO.class);
        return fileDTO;
    }
}
