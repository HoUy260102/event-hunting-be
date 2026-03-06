package com.example.event.repository;

import com.example.event.entity.User;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    @Query("""
                SELECT DISTINCT u FROM User u
                LEFT JOIN FETCH u.role r
                LEFT JOIN FETCH r.permissions
                WHERE u.email = :email
            """)
    User findUserByEmail(@Param("email") String email);
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.avatar " +
            "LEFT JOIN FETCH u.role " +
            "WHERE u.id = :id")
    User findUserByIdWithDetails(@Param("id") String id);
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.avatar " +
            "LEFT JOIN FETCH u.role " +
            "WHERE u.id = :id")
    User findUserByIdForUpdate(@Param("id") String id);
    User findUserById(String id);
    boolean existsUserByEmail(String email);
    boolean existsUserById(String id);
}
