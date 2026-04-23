package com.example.event.mapper;

import com.example.event.dto.AuthDTO;
import com.example.event.entity.User;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuthMapper {
    private final ModelMapper modelMapper;

    public AuthDTO toDTO(User user) {
        AuthDTO authDTO = modelMapper.map(user, AuthDTO.class);
        authDTO.setRole(Optional.ofNullable(user.getRole())
                .map(role -> role.getName())
                .orElse(null));
        authDTO.setRoleId(Optional.ofNullable(user.getRole())
                .map(role -> role.getId())
                .orElse(null));
        List<String> permissions = Optional.ofNullable(user.getRole())
                .map(role -> role.getPermissions())
                .orElse(Collections.emptySet())
                .stream()
                .map(per -> per.getCode())
                .collect(Collectors.toList());
        authDTO.setAvatarUrl(user.getAvatar().getUrl());
        authDTO.setPassword("");
        authDTO.setPermissions(permissions);
        return authDTO;
    }
}
