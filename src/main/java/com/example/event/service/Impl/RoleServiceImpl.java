package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.RoleStatus;
import com.example.event.dto.RoleDTO;
import com.example.event.dto.request.CreateRoleReq;
import com.example.event.entity.Permission;
import com.example.event.entity.Role;
import com.example.event.exception.AppException;
import com.example.event.mapper.RoleMapper;
import com.example.event.repository.PermissionRepository;
import com.example.event.repository.RoleRepository;
import com.example.event.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;
    private final PermissionRepository permissionRepository;
    private final SecurityUtils securityUtils;

    @Override
    public void updatePermissions(String roleId, List<String> permissionIds) {
        String updatorId = securityUtils.getCurrentUserId();
        Role role = roleRepository.findRoleById(roleId);
        Long currentPerVer = role.getPermissionVersion();
        // Lấy permission mới
        Set<Permission> newPermissions =
                new HashSet<>(permissionRepository.findAllById(permissionIds));
        role.setPermissions(newPermissions);
        role.setPermissionVersion(currentPerVer + 1);
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(updatorId);
        roleRepository.save(role);
        log.info("[ROLE] Cập nhật danh sách quyền cho vai trò thành công: roleId={}, totalPermissions={}, updatorId={}",
                roleId, newPermissions.size(), updatorId);
    }

    @Override
    public RoleDTO createRole(CreateRoleReq req) {
        if (roleRepository.existsRoleByName(req.getName().toUpperCase())) {
            log.warn("[ROLE] Tạo vai trò thất bại do tên vai trò đã tồn tại: name={}", req.getName().toUpperCase());
            throw new AppException(ErrorCode.ROLE_EXISTS);
        }
        String creatorId = securityUtils.getCurrentUserId();
        Set<Permission> permissions = permissionRepository.findAllById(req.getPermissionIds()).stream().collect(Collectors.toSet());
        Role role = new Role();
        role.setName(req.getName().toUpperCase());
        role.setDescription(req.getDescription());
        role.setStatus(RoleStatus.ACTIVE);
        role.setCreatedAt(LocalDateTime.now());
        role.setCreatedBy(creatorId);
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(creatorId);
        role.setPermissions(permissions);
        roleRepository.save(role);
        log.info("[ROLE] Tạo vai trò mới thành công: roleId={}, name={}, totalPermissions={}, creatorId={}",
                role.getId(), role.getName(), permissions.size(), creatorId);
        return roleMapper.toDTO(role);
    }

    @Override
    public List<RoleDTO> getAllRole() {
        List<Role> roles = roleRepository.findAll();
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }
        List<RoleDTO> roleDTOS = roles.stream().map(roleMapper::toDTO).collect(Collectors.toList());
        return roleDTOS;
    }

    @Override
    public List<Map<String, String>> getAllRoleForSelect() {
        return roleRepository.findAll().stream()
                .map(role -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("id", role.getId());
                    map.put("name", role.getName());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
