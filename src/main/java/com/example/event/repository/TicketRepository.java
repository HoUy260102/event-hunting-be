package com.example.event.repository;

import com.example.event.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, String>, JpaSpecificationExecutor<Ticket> {
    @Query("""
        SELECT t from Ticket t
        JOIN FETCH t.event e
        JOIN FETCH t.user u
        JOIN FETCH t.show s
        JOIN FETCH t.reservation r
        LEFT JOIN FETCH e.poster p
        WHERE t.id = :ticketId AND t.deletedAt IS NULL
    """)
    Ticket findTicketDetailsById(@Param("ticketId") String ticketId);
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);
}
