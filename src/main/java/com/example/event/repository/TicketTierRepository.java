package com.example.event.repository;

import com.example.event.entity.TicketTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketTierRepository extends JpaRepository<TicketTier, String> {
    @Modifying
    @Query("""
                UPDATE TicketTier tr 
                SET tr.deletedAt = :now,
                    tr.deletedBy = :deletorId
                WHERE tr.ticketType.show.id IN :showIds
                  AND tr.deletedAt IS NULL
            """)
    void softDeleteTiersByShowIds(
            @Param("showIds") List<String> showIds,
            @Param("now") LocalDateTime now,
            @Param("deletorId") String deletorId
    );

    @Modifying
    @Query("""
                UPDATE TicketTier tr 
                SET tr.deletedAt = :now,
                    tr.deletedBy = :deletorId
                WHERE tr.ticketType.id IN :typeIds
                  AND tr.deletedAt IS NULL
            """)
    void softDeleteTiersByTypeIds(
            @Param("typeIds") List<String> typeIds,
            @Param("now") LocalDateTime now,
            @Param("deletorId") String deletorId
    );

    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE TicketTier tr 
                SET tr.deletedAt = :now,
                    tr.deletedBy = :deletorId
                WHERE tr.id IN :tierIds
                  AND tr.deletedAt IS NULL
            """)
    void softDeleteTiersByIds(
            @Param("tierIds") List<String> tierIds,
            @Param("now") LocalDateTime now,
            @Param("deletorId") String deletorId
    );

    @Modifying
    @Query("UPDATE TicketTier tt SET tt.reservedQuantity = tt.reservedQuantity + :qty, " +
            "tt.updatedAt = :now, " +
            "tt.updatedBy = :updatorId " +
            "WHERE tt.id = :id " +
            "AND (tt.reservedQuantity + :qty) <= tt.limitQuantity " +
            "AND tt.deletedAt IS NULL")
    int incrementReservedQuantity(@Param("id") String id,
                                  @Param("qty") Integer qty,
                                  @Param("now") LocalDateTime now,
                                  @Param("updatorId") String updatorId);

    @Modifying
    @Query("UPDATE TicketTier tt SET tt.soldQuantity = tt.soldQuantity + :qty, " +
            "tt.updatedAt = :now, " +
            "tt.updatedBy = :updatorId " +
            "WHERE tt.id = :id " +
            "AND (tt.soldQuantity + :qty) <= tt.limitQuantity " +
            "AND tt.deletedAt IS NULL")
    int incrementSoldQuantity(@Param("id") String id,
                                  @Param("qty") Integer qty,
                                  @Param("now") LocalDateTime now,
                                  @Param("updatorId") String updatorId);

    @Modifying
    @Query("UPDATE TicketTier tt SET " +
            "tt.reservedQuantity = CASE WHEN (tt.reservedQuantity - :qty) < 0 THEN 0 ELSE (tt.reservedQuantity - :qty) END, " +
            "tt.updatedAt = :now, " +
            "tt.updatedBy = :updatorId " +
            "WHERE tt.id = :id AND tt.deletedAt IS NULL")
    int decrementReservedQuantity(@Param("id") String id,
                                  @Param("qty") Integer qty,
                                  @Param("now") LocalDateTime now,
                                  @Param("updatorId") String updatorId);

    TicketTier findTicketTierById(String id);
    List<TicketTier> findTicketTiersByTicketType_IdAndDeletedAtIsNull(String ticketTypeId);
    List<TicketTier> findTicketTiersByTicketType_IdInAndDeletedAtIsNull(List<String> typeIds);
}
