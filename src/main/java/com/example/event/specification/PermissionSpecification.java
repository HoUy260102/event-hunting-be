package com.example.event.specification;

import com.example.event.entity.Permission;
import org.springframework.data.jpa.domain.Specification;

public class PermissionSpecification {
    public static Specification<Permission> hasNextId(String nextId) {
        return (root, query, cb) -> nextId == null ? null :
                cb.greaterThan(root.get("id"), nextId);
    }

    public static Specification<Permission> hasName(String name) {
        return (root, query, cb) -> name == null ? null :
                cb.like(cb.lower(root.get("name")), "%" + name + "%");
    }

    public static Specification<Permission> hasCode(String code) {
        return (root, query, cb) -> code == null ? null :
                cb.like(cb.lower(root.get("code")), "%" + code + "%");
    }

    public static Specification<Permission> hasModule(String module) {
        return (root, query, cb) -> module == null ? null :
                cb.like(cb.lower(root.get("module")), "%" + module + "%");
    }

    public static Specification<Permission> notDisable() {
        return (root, query, cb) -> cb.isFalse(root.get("disable"));
    }
}
