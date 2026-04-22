package com.example.event.repository;

import com.example.event.entity.Ticket;
import com.example.event.projection.TicketStatProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    @Query("""
                SELECT count(t) from Ticket t
                WHERE t.show.id = :showId AND t.deletedAt IS NULL
            """)
    int countTotalIssuedTickets(@Param("showId") String showId);

    @Query("""
                SELECT count(t) from Ticket t
                WHERE t.show.id = :showId AND t.deletedAt IS NULL AND t.status = 'USED'
            """)
    int countTotalCheckedInTickets(@Param("showId") String showId);

    @Query("""
                SELECT count(t) from Ticket t
                WHERE t.show.id = :showId AND t.deletedAt IS NULL AND t.status <> 'USED'
            """)
    int countTotalRemainingTickets(@Param("showId") String showId);

    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    Ticket findTicketByQrCode(String qrCode);

    Ticket findTicketById(String id);

    @Query("""
            SELECT 
                e.id                                    as eventId,
                e.name                                  as eventName,
                e.location                              as eventLocation,
                e.startTime                             as eventStartTime,
                e.endTime                               as eventEndTime,
                e.status                                as eventStatus,
                f.url                                   as eventPosterUrl,
                
                s.id                                    as showId,
                s.startTime                             as showStartTime,
                s.endTime                               as showEndTime,
                s.status                                as showStats,
                EXTRACT(MONTH FROM s.startTime)         AS showStartDay,
                EXTRACT(DAY FROM s.startTime)           AS showStartMonth,
                
                tt.id                                   as ticketTypeId,
                tt.name                                 as typeTypeName,
                tt.totalQuantity                        as ticketTypeTotalQuantity,
                tt.soldQuantity                         as ticketTypeSoldQuantity,
                tt.reservedQuantity                     as ticketTypeReservedQuantity,
                tt.totalQuantity - tt.reservedQuantity  as ticketTypeAvailableQuantity,
                tt.status                               as ticketTypeStatus,
                
                ti.id                                   as ticketTierId,
                ti.name                                 as ticketTierName,
                ti.limitQuantity                        as ticketTierLimitQuantity,
                ti.soldQuantity                         as ticketTierSoldQuantity,
                ti.reservedQuantity                     as ticketTierReservedQuantity,
                
                COALESCE(SUM(ri.totalPrice), 0)  as ticketTierTotalPrice,
                COALESCE(SUM(ri.discountAmount), 0) as ticketTierTotalDiscountAmount,
                COALESCE(SUM(ri.finalPrice), 0)     as ticketTierFinalPrice
                
            FROM Event e
            JOIN File f             ON f.referenceId = e.id AND f.folder = 'EVENT_POSTER' AND f.status = 'ACTIVE'
            JOIN Show s             ON s.event.id = e.id
            JOIN TicketType tt      ON tt.show.id = s.id
            JOIN TicketTier ti            ON ti.ticketType.id = tt.id
            LEFT JOIN ReservationItem ri ON ri.ticketTier.id = ti.id
            WHERE e.id = :eventId
            GROUP BY s.id, tt.id, ti.id
            ORDER BY s.startTime, tt.name, ti.name
            """)
    List<TicketStatProjection> getStatByEvent(@Param("eventId") Long eventId);
}
