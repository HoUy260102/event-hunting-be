package com.example.event.repository;

import com.example.event.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CategoryRepository extends JpaRepository<Category, String>, JpaSpecificationExecutor<Category> {
    boolean existsCategoryByName(String name);
    boolean existsCategoryBySlug(String slug);
    boolean existsCategoryByNameAndIdNot(String name, String id);
    boolean existsCategoryBySlugAndIdNot(String slug, String id);
    Category findCategoryById(String id);
}
