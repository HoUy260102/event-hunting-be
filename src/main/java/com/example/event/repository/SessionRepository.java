package com.example.event.repository;

import com.example.event.entity.Session;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, String> {
    Session findByDeviceIdAndUser_Id(String deviceId, String userId);

    Session findSessionById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.tokenFamily = :tokenFamily order by s.id")
    List<Session> findAllByTokenFamilyForUpdate(@Param("tokenFamily") String tokenFamily);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.refreshTokenHash = :refreshTokenHash")
    Optional<Session> findByRefreshTokenHashForUpdate(
            @Param("refreshTokenHash") String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.id = :id")
    Optional<Session> findSessionByIdForUpdate(@Param("id") String id);
}
