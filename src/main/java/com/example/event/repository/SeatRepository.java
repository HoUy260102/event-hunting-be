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

    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE Seat s 
                SET s.status = 'HOLD',
                    s.reservedBy.id = :userId,  
                    s.updatedBy = :userId,
                    s.updatedAt = :now
                WHERE s.id = :seatId
                  AND s.status = 'AVAILABLE'
                  AND s.deletedAt IS NULL
            """)
    int holdSeat(@Param("seatId") String seatId,
                 @Param("userId") String userId,
                 @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE Seat s 
                SET s.status = 'AVAILABLE',
                    s.reservedBy = null,
                    s.updatedAt = :now
                WHERE s.id IN (
                    SELECT ri.seat.id FROM ReservationItem ri WHERE ri.reservation.id = :reservationId
                )
                AND s.status = 'HOLD'
            """)
    int releaseAllSeatsByReservation(@Param("reservationId") String reservationId,
                                     @Param("now") LocalDateTime now);

    @Query(value = "SELECT * FROM seat " +
            "WHERE status = 'AVAILABLE' AND ticket_type_id = :typeId " +
            "ORDER BY queue_no ASC " +
            "LIMIT :quantity FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Seat> findBestAvailableUnassignedSeats(@Param("typeId") String typeId, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE Seat s " +
            "SET s.status = 'BOOKED', " +
            "s.updatedBy = :updatedBy, " +
            "s.updatedAt = :now " +
            "WHERE s.id IN :ids AND (s.status = 'AVAILABLE' or s.status = 'HOLD')")
    void markSeatsAsBooked(@Param("ids") List<String> ids,
                           @Param("updatedBy") String updatedBy,
                           @Param("now") LocalDateTime now);

    List<Seat> findSeatsByTicketType_IdAndDeletedAtIsNull(String ticketTypeId);
    Seat findSeatById(String id);
}
