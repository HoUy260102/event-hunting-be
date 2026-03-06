package com.example.event.mapper;

import com.example.event.dto.UserDTO;
import com.example.event.entity.User;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;
@Component
@RequiredArgsConstructor
public class UserMapper {
    private final ModelMapper modelMapper;
    private final RoleMapper roleMapper;
    private final FileMapper fileMapper;
    public UserDTO toDTO(User user) {
        UserDTO userDTO = modelMapper.map(user, UserDTO.class);
        userDTO.setRole(Optional.ofNullable(roleMapper.toDTO(user.getRole())).orElse(null));
        userDTO.setAvatar(Optional.ofNullable(fileMapper.toDTO(user.getAvatar())).orElse(null));
        return userDTO;
    }
}
