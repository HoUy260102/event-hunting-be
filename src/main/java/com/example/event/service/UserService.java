package com.example.event.service;

import com.example.event.dto.UserDTO;
import com.example.event.dto.request.CreateUserReq;
import com.example.event.dto.request.UpdateUserReq;
import org.springframework.data.domain.Page;

public interface UserService {
    Page<UserDTO> getUserSearch(String keyword, String roleId, String status, int pageNo, int pageSize);
    UserDTO createUser(CreateUserReq req);
    UserDTO updateUser(UpdateUserReq req, String id);
    UserDTO updateProfile(UpdateUserReq req);
    UserDTO findUserById(String id);
    UserDTO softDeleteUser(String id);
    UserDTO restoreUser(String id);
}
