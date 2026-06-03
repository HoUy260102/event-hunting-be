package com.example.event.repository;

import com.example.event.constant.FileStatus;
import com.example.event.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface FileRepository extends JpaRepository<File, String> {
    File findFileById(String id);
    List<File> findFilesByStatusAndReferenceId(FileStatus status, String id);
    @Query("SELECT f FROM File f WHERE f.status = :status AND f.referenceId IN :referenceIds")
    List<File> findFilesByStatusAndReferenceIds(
        @org.springframework.data.repository.query.Param("status") FileStatus status, 
        @org.springframework.data.repository.query.Param("referenceIds") List<String> referenceIds
    );
    int countFileByIdIn(List<String> ids);
    @Modifying
    @Transactional
    @Query("UPDATE File f SET f.status = 'ACTIVE', f.referenceId = :referenceId " +
            "WHERE f.id IN :fileIds AND f.status = 'PENDING'")
    int activateFiles(List<String> fileIds, String referenceId);

    @Modifying
    @Transactional
    @Query("UPDATE File f SET f.status = 'DELETED', f.deletedAt = :deletedAt, f.referenceId = null " +
            "WHERE f.id IN :fileIds AND f.status = 'ACTIVE'")
    int deleteFile(List<String> fileIds, LocalDateTime deletedAt);

    @Query("SELECT f FROM File f WHERE " +
            "(f.status = 'PENDING' AND f.createdAt < :pendingThreshold) OR " +
            "(f.status = 'DELETED' AND f.deletedAt < :deletedThreshold)")
    org.springframework.data.domain.Slice<File> findExpiredFiles(
            java.time.LocalDateTime pendingThreshold,
            java.time.LocalDateTime deletedThreshold,
            org.springframework.data.domain.Pageable pageable
    );
}
