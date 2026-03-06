package com.example.event.specification;

import com.example.event.constant.CategoryStatus;
import com.example.event.entity.Category;
import org.springframework.data.jpa.domain.Specification;

public class CategorySpecifiation {
    public static Specification<Category> hasName(String name) {
        return (root, query, cb) ->
                name == null ? null : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Category> hasSlug(String slug) {
        return (root, query, cb) ->
                slug == null ? null : cb.like(cb.lower(root.get("slug")), "%" + slug.toLowerCase() + "%");
    }

    public static Specification<Category> hasId(String id) {
        return (root, query, cb) ->
                id == null ? null : cb.equal(root.get("id"), id);
    }

    public static Specification<Category> hasStatus(CategoryStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Category> isNotDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Category> isDeleted() {
        return (root, query, cb) ->
                cb.isNotNull(root.get("deletedAt"));
    }


}
