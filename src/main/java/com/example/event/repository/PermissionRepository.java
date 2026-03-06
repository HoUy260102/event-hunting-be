package com.example.event.repository;

import com.example.event.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, String>, JpaSpecificationExecutor<Permission> {
    List<Permission> getPermissionsByIdIn(List<String> ids);
    boolean existsByCode(String code);
    Permission findPermissionById(String perId);
    List<Permission> findAllByRoles_Id(String roleId);
}
