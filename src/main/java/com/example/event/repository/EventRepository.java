package com.example.event.repository;

import com.example.event.entity.Event;
import com.example.event.dto.response.TopEventProjection;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, String>, JpaSpecificationExecutor<Event> {
    Event findEventById(String id);
    @Query("SELECT e FROM Event e " +
            "LEFT JOIN FETCH e.banner " +
            "LEFT JOIN FETCH e.poster " +
            "LEFT JOIN FETCH e.organizerLogo " +
            "LEFT JOIN FETCH e.category " +
            "LEFT JOIN FETCH e.province " +
            "LEFT JOIN FETCH e.user u " +
            "LEFT JOIN FETCH u.role " +
            "LEFT JOIN FETCH u.avatar " +
            "WHERE e.id = :id")
    Event findEventByIdForDetails(@Param("id") String id);



    @Query(value = """
        SELECT * FROM event 
        WHERE (
            (MATCH(name, location, organizer_name) AGAINST (:keyword IN NATURAL LANGUAGE MODE) * 2) + 
            (MATCH(description_text) AGAINST (:keyword IN NATURAL LANGUAGE MODE))
        ) > 0.2
        ORDER BY (
            (MATCH(name, location, organizer_name) AGAINST (:keyword IN NATURAL LANGUAGE MODE) * 2) + 
            (MATCH(description_text) AGAINST (:keyword IN NATURAL LANGUAGE MODE))
        ) DESC
        """, nativeQuery = true)
    List<Event> searchFullTextBoolean(@Param("keyword") String keyword);

    List<Event> findEventsByUser_Id(String userId);
    List<Event> findAllByDeletedAtIsNull();
    boolean existsByCategoryIdAndDeletedAtIsNull(String categoryId);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.user.id = :userId AND e.deletedAt IS NULL AND e.createdAt BETWEEN :start AND :end")
    Long countCreatedEventsByUserId(@Param("userId") String userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.deletedAt IS NULL AND e.createdAt BETWEEN :start AND :end")
    Long countCreatedEventsAll(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
        SELECT 
            e.id AS id,
            e.name AS name,
            COALESCE((SELECT SUM(r.finalAmount) FROM Reservation r WHERE r.status = 'PAID' AND r.event.id = e.id AND r.createdAt BETWEEN :start AND :end), 0) AS revenue,
            COALESCE((SELECT SUM(ri.quantity) FROM ReservationItem ri JOIN ri.reservation r WHERE r.status = 'PAID' AND r.event.id = e.id AND r.createdAt BETWEEN :start AND :end), 0) AS ticketsSold
        FROM Event e
        WHERE e.user.id = :userId AND e.deletedAt IS NULL
        ORDER BY revenue DESC, ticketsSold DESC
    """)
    List<TopEventProjection> findTopEventsByUserIdAndDateRange(
        @Param("userId") String userId, 
        @Param("start") LocalDateTime start, 
        @Param("end") LocalDateTime end,
        org.springframework.data.domain.Pageable pageable
    );
}
