package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Permission {
    @Id
    @UlidID
    private String id;
    private String code;
    private String name;
    private String module;
    private Boolean disable;

    @ManyToMany(mappedBy = "permissions")
    private List<Role> roles;

    // Thông tin meta
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
