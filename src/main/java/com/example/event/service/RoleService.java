package com.example.event.service;

import com.example.event.dto.RoleDTO;
import com.example.event.dto.request.CreateRoleReq;

import java.util.List;
import java.util.Map;

public interface RoleService {
    void updatePermissions(String roleId, List<String> permissionIds);
    RoleDTO createRole(CreateRoleReq req);
    List<RoleDTO> getAllRole();
    List<Map<String, String>> getAllRoleForSelect();
}
