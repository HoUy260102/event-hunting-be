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

    boolean existsByEvent_IdAndUser_IdAndTypeAndCreatedAtAfter(String eventId, String userId, InteractionType type,
            LocalDateTime since);

    void deleteByEvent_IdAndUser_IdAndType(String eventId, String userId, InteractionType type);

    List<EventInteraction> findByCreatedAtAfter(LocalDateTime since);

    List<EventInteraction> findByUser_IdAndType(String userId, InteractionType type);

    @Query(value = "SELECT DISTINCT user_id FROM event_interaction WHERE created_at >= :since", nativeQuery = true)
    List<String> findActiveUserIds(@Param("since") LocalDateTime since);

    @Query(value = """
            SELECT DISTINCT user_id
            FROM event_interaction
            WHERE created_at >= :since
              AND (:lastUserId = '' OR user_id > :lastUserId)
            ORDER BY user_id ASC
            LIMIT :batchSize
            """, nativeQuery = true)
    List<String> findActiveUserIdsKeyset(
            @Param("since") LocalDateTime since,
            @Param("lastUserId") String lastUserId,
            @Param("batchSize") int batchSize);

    @Query(value = """
            SELECT
                i.user_id AS userId,
                e.sequence_id AS sequenceId,
                MAX(
                    CASE i.type
                        WHEN 'VIEW'     THEN 1.0
                        WHEN 'COMMENT'  THEN 2.0
                        WHEN 'FAVORITE' THEN 3.0
                        WHEN 'REGISTER' THEN 5.0
                        ELSE 0.0
                    END
                ) AS maxWeight
            FROM event_interaction i
            JOIN event e ON e.id = i.event_id
            WHERE i.user_id IN (:userIds)
              AND i.created_at >= :since
              AND e.sequence_id IS NOT NULL
            GROUP BY i.user_id, e.sequence_id
            """, nativeQuery = true)
    List<Object[]> findUserInteractionsByUsers(
            @Param("userIds") List<String> userIds,
            @Param("since") LocalDateTime since);

    @Query(value = """
            SELECT
                user_event.event_id,
                SUM(user_event.user_score) AS total_score
            FROM (
                SELECT
                    i.user_id,
                    i.event_id,
                    MAX(
                        CASE i.type
                            WHEN 'VIEW'     THEN 1.0
                            WHEN 'COMMENT'  THEN 2.0
                            WHEN 'FAVORITE' THEN 3.0
                            WHEN 'REGISTER' THEN 5.0
                            ELSE 0.0
                        END
                    ) AS user_score
                FROM event_interaction i
                JOIN event e ON e.id = i.event_id
                WHERE i.user_id IN (:userIds)
                  AND i.created_at >= :since
                  AND e.status = 'PUBLISHED'
                  AND e.deleted_at IS NULL
                  AND e.end_time > NOW()
                GROUP BY i.user_id, i.event_id
            ) AS user_event
            GROUP BY user_event.event_id
            ORDER BY total_score DESC
            """, nativeQuery = true)
    List<Object[]> findCandidateEventIdsWithScore(
            @Param("userIds") List<String> userIds,
            @Param("since") LocalDateTime since);

    @Query(value = """
            SELECT
                i.user_id AS userId,
                i.event_id AS eventId,
                MAX(
                    CASE i.type
                        WHEN 'VIEW'     THEN 1.0
                        WHEN 'COMMENT'  THEN 2.0
                        WHEN 'FAVORITE' THEN 3.0
                        WHEN 'REGISTER' THEN 5.0
                        ELSE 0.0
                    END
                ) AS maxWeight
            FROM event_interaction i
            WHERE i.created_at >= :since
            GROUP BY i.user_id, i.event_id
            """, nativeQuery = true)
    List<Object[]> findAggregatedInteractions(@Param("since") LocalDateTime since);

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
                            WHEN 'COMMENT'  THEN 1.2
                            WHEN 'FAVORITE' THEN 1.5
                            WHEN 'REGISTER' THEN 2.0
                            ELSE 0
                        END
                        /
                        POW(GREATEST(TIMESTAMPDIFF(HOUR, i.created_at, NOW()), 0) + 2, 1.5)
                    ) AS trendingScore
                FROM event_interaction i
                JOIN event e ON e.id = i.event_id
                LEFT JOIN file f ON e.id = f.reference_id
                    AND f.folder = 'EVENT_POSTER'
                    AND f.status = 'ACTIVE'
                WHERE i.created_at >= :since
                    AND e.end_time >= NOW()
                    AND e.deleted_at IS NULL
                    AND e.status = 'PUBLISHED'
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
            @Param("limit") int limit);
}
