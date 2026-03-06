package com.example.event.repository;

import com.example.event.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, String> {
    List<Show> findShowsByEvent_Id(String id);

    List<Show> findByEvent_IdAndDeletedAtIsNull(String eventId);

    Show findShowById(String id);

    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE Show s
                SET s.deletedAt = :now,
                    s.deletedBy = :deletorId
                WHERE s.id IN :ids
                  AND s.deletedAt IS NULL
            """)
    void softDeleteShows(
            @Param("ids") List<String> ids,
            @Param("now") LocalDateTime now,
            @Param("deletorId") String deletorId
    );

    @Query("""
                SELECT COUNT(s) > 0
                FROM Show s
                WHERE s.event.id = :eventId
                  AND s.deletedAt IS NULL
                  AND s.id <> :showId
                  AND :newStart < s.endTime
                  AND :newEnd   > s.startTime
            """)
    boolean existsOverlappingShowForUpdate(
            @Param("eventId") String eventId,
            @Param("showId") String showId,
            @Param("newStart") LocalDateTime newStart,
            @Param("newEnd") LocalDateTime newEnd
    );

    @Query("""
                SELECT COUNT(s) > 0
                FROM Show s
                WHERE s.event.id = :eventId
                  AND s.deletedAt IS NULL
                  AND :newStart < s.endTime
                  AND :newEnd   > s.startTime
            """)
    boolean existsOverlappingShowForCreate(
            @Param("eventId") String eventId,
            @Param("newStart") LocalDateTime newStart,
            @Param("newEnd") LocalDateTime newEnd
    );
}
