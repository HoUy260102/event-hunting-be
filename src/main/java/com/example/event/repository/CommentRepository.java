package com.example.event.repository;

import com.example.event.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, String> {

    // 1. Phân trang Keyset cho bình luận cha của sự kiện (Mới nhất lên đầu)
    @Query("SELECT c FROM Comment c " +
           "LEFT JOIN FETCH c.user u " +
           "LEFT JOIN FETCH u.avatar " +
           "WHERE c.event.id = :eventId " +
           "AND c.parent IS NULL " +
           "AND c.deletedAt IS NULL " +
           "AND (:nextId IS NULL OR c.id < :nextId) " +
           "ORDER BY c.id DESC")
    Slice<Comment> findParentCommentsKeyset(
        @Param("eventId") String eventId,
        @Param("nextId") String nextId,
        Pageable pageable
    );

    // 2. Phân trang Keyset cho bình luận con (Cũ nhất lên đầu)
    @Query("SELECT c FROM Comment c " +
           "LEFT JOIN FETCH c.user u " +
           "LEFT JOIN FETCH u.avatar " +
           "WHERE c.parent.id = :parentId " +
           "AND c.deletedAt IS NULL " +
           "AND (:nextId IS NULL OR c.id > :nextId) " +
           "ORDER BY c.id ASC")
    Slice<Comment> findRepliesKeyset(
        @Param("parentId") String parentId,
        @Param("nextId") String nextId,
        Pageable pageable
    );

    // 3. Đếm số lượng phản hồi của một bình luận cha
    long countByParentIdAndDeletedAtIsNull(String parentId);

    @Query("SELECT c.parent.id, COUNT(c) FROM Comment c WHERE c.parent.id IN :parentIds AND c.deletedAt IS NULL GROUP BY c.parent.id")
    java.util.List<Object[]> countRepliesForParentIds(@Param("parentIds") java.util.List<String> parentIds);
}
