package com.example.event.service;

import com.example.event.dto.PermissionDTO;
import com.example.event.dto.request.CreatePermissionReq;
import com.example.event.dto.response.KeysetPageResponse;

import java.util.List;

public interface PermissionService {
    PermissionDTO createPermission(CreatePermissionReq req);
    List<PermissionDTO> getAllPermissions();
    List<PermissionDTO> getPermissionsByRoleId(String roleId);
    KeysetPageResponse<PermissionDTO, String> getPermissions(String keyword, String nextId, int size);
}
