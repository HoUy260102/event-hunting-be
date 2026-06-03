package com.example.event.repository;

import com.example.event.entity.CommentReaction;
import com.example.event.constant.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CommentReactionRepository extends JpaRepository<CommentReaction, String> {
    
    // Đếm số lượng lượt thích/cảm xúc của một bình luận
    long countByCommentId(String commentId);
    
    // Tìm cảm xúc của một người dùng trên bình luận cụ thể (để toggle/update)
    Optional<CommentReaction> findByCommentIdAndUserId(String commentId, String userId);

    @org.springframework.data.jpa.repository.Query("SELECT r.comment.id, COUNT(r) FROM CommentReaction r WHERE r.comment.id IN :commentIds GROUP BY r.comment.id")
    java.util.List<Object[]> countLikesForCommentIds(@org.springframework.data.repository.query.Param("commentIds") java.util.List<String> commentIds);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM CommentReaction r WHERE r.comment.id IN :commentIds AND r.user.id = :userId")
    java.util.List<CommentReaction> findReactionsByCommentIdsAndUserId(
        @org.springframework.data.repository.query.Param("commentIds") java.util.List<String> commentIds, 
        @org.springframework.data.repository.query.Param("userId") String userId
    );
}
