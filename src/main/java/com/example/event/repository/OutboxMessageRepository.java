package com.example.event.repository;

import com.example.event.constant.OutboxStatus;
import com.example.event.entity.OutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, String> {

    @Query("SELECT m FROM OutboxMessage m WHERE m.status = :status AND m.retryCount < m.maxRetries ORDER BY m.createdAt ASC")
    List<OutboxMessage> findPendingMessages(@Param("status") OutboxStatus status, Pageable pageable);

    @Query("SELECT m.id FROM OutboxMessage m WHERE m.status = :status AND m.processedAt < :threshold ORDER BY m.processedAt ASC")
    List<String> findOldCompletedMessageIds(
            @Param("status") OutboxStatus status,
            @Param("threshold") LocalDateTime threshold,
            Pageable pageable);

    @Modifying
    @Query("DELETE FROM OutboxMessage m WHERE m.id IN :ids")
    int deleteByIds(@Param("ids") List<String> ids);
}
