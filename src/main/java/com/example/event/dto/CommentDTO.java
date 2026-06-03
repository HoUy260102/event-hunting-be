package com.example.event.dto;

import com.example.event.constant.ReactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommentDTO {
    private String id;
    private String content;
    private CommentUserDTO user;
    private String parentId;
    private List<FileDTO> images;
    private long likesCount;
    private boolean isLiked;
    private ReactionType currentReaction;
    private long repliesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
