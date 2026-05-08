package com.example.event.repository;

import com.example.event.constant.InteractionType;
import com.example.event.entity.EventInteraction;
import com.example.event.projection.EventTrendingProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventInteractionRepository extends JpaRepository<EventInteraction, String> {
    boolean existsByEvent_IdAndUser_IdAndType(String eventId, String userId, InteractionType type);

    void deleteByEvent_IdAndUser_IdAndType(String eventId, String userId, InteractionType type);

    @Query(value = """
                SELECT 
                    e.id AS id,
                    e.name AS name,
                    e.location AS location,
                    f.url AS posterUrl,
                    e.start_time AS startTime,
                    e.end_time AS endTime,
                    SUM(
                        CASE i.type
                            WHEN 'VIEW'     THEN 1.0
                            WHEN 'REGISTER' THEN 2.0
                            WHEN 'FAVORITE' THEN 1.5
                            ELSE 0
                        END
                        /
                        POW(TIMESTAMPDIFF(HOUR, i.created_at, NOW()) + 2, 1.5)
                    ) AS trendingScore
                FROM event_interaction i
                JOIN event e ON e.id = i.event_id
                LEFT JOIN file f ON e.id = f.reference_id 
                    AND f.folder = 'EVENT_POSTER' 
                    AND f.status = 'ACTIVE'
                WHERE i.created_at >= :since
                    AND e.end_time >= NOW() 
                    AND e.deleted_at IS NULL
                GROUP BY 
                    e.id, 
                    e.name, 
                    e.location, 
                    f.url, 
                    e.start_time, 
                    e.end_time
                ORDER BY trendingScore DESC
                LIMIT :limit
            """, nativeQuery = true)
    List<EventTrendingProjection> findTrendingEvents(
            @Param("since") LocalDateTime since,
            @Param("limit") int limit
    );
}
