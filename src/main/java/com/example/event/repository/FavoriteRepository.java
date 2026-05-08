package com.example.event.repository;

import com.example.event.entity.Favorite;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {
    boolean existsByUser_IdAndEvent_Id(String userId, String eventId);

    Favorite findFavoriteByUser_IdAndEvent_Id(String userId, String eventId);

    @Query("SELECT f.event.id FROM Favorite f WHERE f.user.id = :userId AND f.event.id IN :eventIds")
    Set<String> findSavedEventIds(String userId, List<String> eventIds);

    @Query("SELECT f FROM Favorite f " +
            "JOIN FETCH f.event " +
            "WHERE f.user.id = :userId AND f.event.deletedAt IS NULL " +
            "AND (:nextId IS NULL OR " +
            "    (f.createdAt < (SELECT f2.createdAt FROM Favorite f2 WHERE f2.id = :nextId) " +
            "    OR (f.createdAt = (SELECT f2.createdAt FROM Favorite f2 WHERE f2.id = :nextId) AND f.id < :nextId))) " +
            "ORDER BY f.createdAt DESC, f.id DESC")
    Slice<Favorite> findMyFavoritesKeyset(@Param("userId") String userId,
                                          @Param("nextId") String nextId,
                                          Pageable pageable);
}
