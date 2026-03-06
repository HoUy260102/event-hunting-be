package com.example.event.repository;

import com.example.event.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, String> {
    Session findByDeviceIdAndUser_Id(String deviceId, String userId);
    Session findByRefreshTokenAndDeviceId(String refreshToken, String deviceId);
    Session findSessionById(String id);
}
