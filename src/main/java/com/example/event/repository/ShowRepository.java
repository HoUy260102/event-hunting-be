package com.example.event.repository;

import com.example.event.dto.ShowRegistryDTO;
import com.example.event.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, String> {
    List<Show> findShowsByEvent_Id(String id);
    List<Show> findByEvent_IdAndDeletedAtIsNull(String eventId);
    Show findShowById(String id);
    @Query("""
                SELECT s FROM Show s 
                WHERE s.endTime <= :thresholdDate 
                AND s.isCleanedUp = false
                ORDER BY s.endTime ASC
            """)
    List<Show> findExpiredShows(LocalDateTime thresholdDate, Pageable pageable);

    @Modifying
    @Transactional
    @Query("""
                UPDATE Show s
                SET s.isCleanedUp = true
                WHERE s.id IN :ids
            """)
    int markAsCleanedUp(List<String> ids);

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

    @Query("SELECT new com.example.event.dto.ShowRegistryDTO(" +
            "es.id, e.name, e.location, es.startTime, es.endTime, es.status, " +
            "CAST(count(t) as int), " +
            "CAST(sum(case when t.status = 'USED' then 1 else 0 end) as int), " +
            "CAST(sum(case when t.status <> 'USED' then 1 else 0 end) as int)) " +
            "FROM Show es " +
            "JOIN es.event e " +
            "LEFT JOIN Ticket t ON t.show = es " +
            "WHERE es.id = :showId AND es.deletedAt IS NULL " +
            "GROUP BY es.id, e.name, e.location, es.startTime, es.endTime, es.status")
    ShowRegistryDTO findShowRegistryById(@Param("showId") String showId);
}
