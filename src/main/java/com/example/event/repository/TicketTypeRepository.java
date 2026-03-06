package com.example.event.repository;

import com.example.event.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketTypeRepository extends JpaRepository<TicketType, String> {
    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE TicketType tt
                SET tt.deletedAt = :now,
                    tt.deletedBy = :deletorId
                WHERE tt.show.id IN :showIds
                  AND tt.deletedAt IS NULL
            """)
    void softDeleteTypesByShowIds(
            @Param("showIds") List<String> showIds,
            @Param("now") LocalDateTime now,
            @Param("deletorId") String deletorId
    );

    @Modifying(clearAutomatically = true)
    @Query("""
                UPDATE TicketType tt
                SET tt.deletedAt = :now,
                    tt.deletedBy = :deletorId
                WHERE tt.id IN :typeIds
                  AND tt.deletedAt IS NULL
            """)
    void softDeleteTypesByIds(
            @Param("typeIds") List<String> typeIds,
            @Param("now") LocalDateTime now,
            @Param("deletorId") String deletorId
    );

    TicketType findTicketTypeById(String id);
    List<TicketType> findTicketTypesByShow_IdAndDeletedAtIsNull(String showId);
    List<TicketType> findTicketTypesByShow_IdInAndDeletedAtIsNull(List<String> showIds);
}

