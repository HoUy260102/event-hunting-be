package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.dto.PermissionDTO;
import com.example.event.dto.request.CreatePermissionReq;
import com.example.event.dto.response.KeysetPageResponse;
import com.example.event.entity.Permission;
import com.example.event.exception.AppException;
import com.example.event.mapper.PermissionMapper;
import com.example.event.repository.PermissionRepository;
import com.example.event.service.PermissionService;
import com.example.event.specification.PermissionSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;
    private final SecurityUtils securityUtils;

    @Override
    public PermissionDTO createPermission(CreatePermissionReq req) {
        if (permissionRepository.existsByCode(req.getCode())) {
            throw new AppException(ErrorCode.PERMISSION_EXISTS);
        }
        String creatorId = securityUtils.getCurrentUserId();
        Permission permission = new Permission();
        permission.setCode(req.getCode());
        permission.setName(req.getName());
        permission.setModule(req.getModule());
        permission.setDisable(false);
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        permission.setCreatedBy(creatorId);
        permission.setUpdatedBy(creatorId);
        permissionRepository.save(permission);
        return permissionMapper.toDTO(permission);
    }

    @Override
    public List<PermissionDTO> getAllPermissions() {
        List<Permission> permissions = permissionRepository.findAll();
        if (permissions.isEmpty()) {
            throw new AppException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        List<PermissionDTO> permissionDTOS = permissions.stream()
                .map(per -> permissionMapper.toDTO(per))
                .collect(Collectors.toList());
        return permissionDTOS;
    }

    @Override
    public List<PermissionDTO> getPermissionsByRoleId(String roleId) {
        List<Permission> permissions = permissionRepository.findAllByRoles_Id(roleId);
        if (permissions.isEmpty()) {
            throw new AppException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        List<PermissionDTO> permissionDTOS = permissions.stream()
                .map(per -> permissionMapper.toDTO(per))
                .collect(Collectors.toList());
        return permissionDTOS;
    }

    @Override
    public KeysetPageResponse<PermissionDTO, String> getPermissions(String keyword, String nextId, int size) {
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        Pageable pageable = PageRequest.of(0, size, sort);
        Specification<Permission> spec = Specification.where(PermissionSpecification.notDisable());
        if (keyword != null) {
            spec = spec.and(Specification.anyOf(
                    PermissionSpecification.hasCode(keyword),
                    PermissionSpecification.hasName(keyword),
                    PermissionSpecification.hasModule(keyword)
            ));
        }
        if (nextId != null && !nextId.trim().equals("")) {
            spec = spec.and(PermissionSpecification.hasNextId(nextId));
        }
        Slice<Permission> permissionSlice = permissionRepository.findAll(spec, pageable);
        List<PermissionDTO> dtos = permissionSlice.getContent().stream()
                .map(permissionMapper::toDTO).collect(Collectors.toList());
        String lastIdInPage = dtos.isEmpty() ? null : dtos.get(dtos.size() - 1).getId();
        return new KeysetPageResponse<>(
                dtos,
                lastIdInPage,
                permissionSlice.hasNext()
        );
    }
}
