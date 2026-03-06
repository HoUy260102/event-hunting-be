package com.example.event.repository;

import com.example.event.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinceRepository extends JpaRepository<Province, String> {
    boolean existsProvinceByName(String name);
    Province findProvinceById(String id);
}
