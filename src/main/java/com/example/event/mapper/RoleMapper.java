package com.example.event.mapper;

import com.example.event.dto.PermissionDTO;
import com.example.event.dto.RoleDTO;
import com.example.event.entity.Role;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RoleMapper {
    private final ModelMapper modelMapper;
    private final PermissionMapper permissionMapper;
    public RoleDTO toDTO(Role role) {
        if (role == null) return null;
        RoleDTO roleDTO = modelMapper.map(role, RoleDTO.class);
        Set<PermissionDTO> permissions = Optional.ofNullable(role.getPermissions()).orElse(new HashSet<>())
                .stream().map(
                        p -> permissionMapper.toDTO(p)
                ).collect(Collectors.toSet());
        roleDTO.setPemissions(permissions);
        return roleDTO;
    }
}
