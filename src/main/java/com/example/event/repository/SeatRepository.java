package com.example.event.repository;

import com.example.event.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, String> {
    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE Seat s 
                SET s.deletedAt = :now,
                    s.deletedBy = :deletorId
                WHERE s.id IN :seatIds
                  AND s.deletedAt IS NULL
            """)
    void softDeleteSeatsByIds(
            @Param("seatIds") List<String> seatIds,
            @Param("now") LocalDateTime now,
            @Param("deletorId") String deletorId
    );

    List<Seat> findSeatsByTicketType_IdAndDeletedAtIsNull(String ticketTypeId);
}
