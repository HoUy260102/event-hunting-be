package com.example.event.repository;

import com.example.event.entity.Event;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, String>, JpaSpecificationExecutor<Event> {
    Event findEventById(String id);
    @Query("SELECT e FROM Event e " +
            "LEFT JOIN FETCH e.banner " +
            "LEFT JOIN FETCH e.poster " +
            "LEFT JOIN FETCH e.organizerLogo " +
            "LEFT JOIN FETCH e.category " +
            "LEFT JOIN FETCH e.province " +
            "WHERE e.id = :id")
    Event findEventByIdForDetails(@Param("id") String id);
}
