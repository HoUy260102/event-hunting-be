package com.example.event.mapper;

import com.example.event.dto.PermissionDTO;
import com.example.event.entity.Permission;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PermissionMapper {
    private final ModelMapper modelMapper;
    public PermissionDTO toDTO(Permission permission) {
        PermissionDTO permissionDTO = modelMapper.map(permission, PermissionDTO.class);
        return permissionDTO;
    }
}
