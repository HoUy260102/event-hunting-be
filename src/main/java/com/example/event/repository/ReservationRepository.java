package com.example.event.repository;

import com.example.event.constant.ReservationStatus;
import com.example.event.entity.Reservation;
import com.example.event.projection.AnalyticsOverviewProjection;
import com.example.event.projection.TicketTierDistributionProjection;
import com.example.event.projection.TopShowProjection;
import com.example.event.projection.TopCustomerProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String>, JpaSpecificationExecutor<Reservation> {
    List<Reservation> findAllByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime expiresAt);
    @Query("""
        select r from Reservation r
        left join fetch r.items
        left join fetch r.event
        left join fetch r.show
        left join fetch r.payment
        where r.id = :reservationId and r.deletedAt is null and r.status = 'PAID'
    """)
    Reservation findReservationSummaryByIdForPaid(@Param("reservationId") String reservationId);
    @Query("""
        select r from Reservation r
        left join fetch r.items
        left join fetch r.event
        left join fetch r.show
        left join fetch r.payment
        where r.id = :reservationId and r.deletedAt is null
    """)
    Reservation findReservationSummaryByIdForAll(@Param("reservationId") String reservationId);
    Reservation findReservationById(String id);
    java.util.Optional<Reservation> findByCode(String code);
    boolean existsByCode(String code);
    java.util.List<Reservation> findByCodeIsNull();
    java.util.List<Reservation> findByStatusAndDeletedAtIsNull(ReservationStatus status);
    boolean existsByUserIdAndDeletedAtIsNull(String userId);
    boolean existsByEventIdAndDeletedAtIsNull(String eventId);
    boolean existsByShowIdAndDeletedAtIsNull(String showId);
    boolean existsByVoucherIdAndDeletedAtIsNull(String voucherId);

    @Query("SELECT COALESCE(SUM(r.finalAmount), 0) FROM Reservation r WHERE r.status = 'PAID' AND r.event.user.id = :userId AND r.createdAt BETWEEN :start AND :end")
    Long sumRevenueByUserId(@Param("userId") String userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(r.finalAmount), 0) FROM Reservation r WHERE r.status = 'PAID' AND r.createdAt BETWEEN :start AND :end")
    Long sumRevenueAll(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM ReservationItem ri JOIN ri.reservation r WHERE r.status = 'PAID' AND r.event.user.id = :userId AND r.createdAt BETWEEN :start AND :end")
    Long sumTicketsSoldByUserId(@Param("userId") String userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM ReservationItem ri JOIN ri.reservation r WHERE r.status = 'PAID' AND r.createdAt BETWEEN :start AND :end")
    Long sumTicketsSoldAll(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.status = 'PAID' AND r.event.user.id = :userId AND r.createdAt BETWEEN :start AND :end")
    Long countBookingsByUserId(@Param("userId") String userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.status = 'PAID' AND r.createdAt BETWEEN :start AND :end")
    Long countBookingsAll(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT r FROM Reservation r LEFT JOIN FETCH r.items WHERE r.status = 'PAID' AND r.event.user.id = :userId AND r.createdAt BETWEEN :start AND :end ORDER BY r.createdAt ASC")
    List<Reservation> findPaidReservationsByUserIdAndDateRange(@Param("userId") String userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT r FROM Reservation r LEFT JOIN FETCH r.items WHERE r.status = 'PAID' AND r.createdAt BETWEEN :start AND :end ORDER BY r.createdAt ASC")
    List<Reservation> findPaidReservationsAllAndDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
        SELECT 
            (SELECT COALESCE(SUM(r.finalAmount), 0) FROM Reservation r WHERE r.status = 'PAID' AND r.event.user.id = u.id AND r.createdAt BETWEEN :start AND :end) AS totalRevenue,
            (SELECT COALESCE(SUM(ri.quantity), 0) FROM ReservationItem ri JOIN ri.reservation r WHERE r.status = 'PAID' AND r.event.user.id = u.id AND r.createdAt BETWEEN :start AND :end) AS totalTicketsSold,
            (SELECT COUNT(r) FROM Reservation r WHERE r.status = 'PAID' AND r.event.user.id = u.id AND r.createdAt BETWEEN :start AND :end) AS totalBookings,
            (SELECT COUNT(e) FROM Event e WHERE e.user.id = u.id AND e.deletedAt IS NULL AND e.createdAt BETWEEN :start AND :end) AS totalEventsCreated
        FROM User u
        WHERE u.id = :userId
    """)
    AnalyticsOverviewProjection getOverviewProjectionByUserId(@Param("userId") String userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
        SELECT 
            r.show.id AS showId,
            r.show.startTime AS startTime,
            COALESCE((SELECT SUM(ri.quantity) FROM ReservationItem ri JOIN ri.reservation res WHERE res.show.id = r.show.id AND res.status = 'PAID' AND res.createdAt BETWEEN :start AND :end), 0) AS ticketsSold,
            SUM(r.finalAmount) AS revenue
        FROM Reservation r
        WHERE r.status = 'PAID' AND r.event.id = :eventId AND r.createdAt BETWEEN :start AND :end
        GROUP BY r.show.id, r.show.startTime
        ORDER BY ticketsSold DESC, revenue DESC
    """)
    List<TopShowProjection> findTopShowsByEventIdAndDateRange(
        @Param("eventId") String eventId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("""
        SELECT 
            ri.ticketTierName AS tierName,
            COALESCE(SUM(ri.quantity), 0) AS quantity
        FROM ReservationItem ri
        JOIN ri.reservation r
        WHERE r.status = 'PAID' AND r.event.user.id = :userId AND r.createdAt BETWEEN :start AND :end
        GROUP BY ri.ticketTierName
        ORDER BY quantity DESC
    """)
    List<TicketTierDistributionProjection> getTicketTierDistributionByUserId(
        @Param("userId") String userId, 
        @Param("start") LocalDateTime start, 
        @Param("end") LocalDateTime end
    );

    @Query("""
        SELECT 
            r.user.id AS userId,
            r.user.name AS name,
            r.user.email AS email,
            r.user.avatar.url AS avatarUrl,
            COUNT(DISTINCT r.id) AS totalBookings,
            SUM(r.finalAmount) AS totalSpent,
            SUM(ri.quantity) AS totalTickets
        FROM ReservationItem ri
        JOIN ri.reservation r
        WHERE r.status = 'PAID' 
          AND r.event.user.id = :userId 
          AND r.createdAt BETWEEN :start AND :end
        GROUP BY r.user.id, r.user.name, r.user.email, r.user.avatar.url
        ORDER BY totalSpent DESC, totalTickets DESC
    """)
    List<TopCustomerProjection> findTopCustomersByOrganizerIdAndDateRange(
        @Param("userId") String userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,
        org.springframework.data.domain.Pageable pageable
    );

    @Query("""
        SELECT COUNT(r.id)
        FROM Reservation r
        WHERE r.status = 'PAID' 
          AND r.event.user.id = :userId 
          AND r.createdAt BETWEEN :start AND :end
        GROUP BY r.user.id
    """)
    List<Long> getOrderCountsPerUser(
        @Param("userId") String userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
