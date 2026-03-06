package com.example.event.repository;

import com.example.event.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {
    Role findByName(String name);
    Role findRoleById(String id);
    boolean existsRoleByName(String name);
}
