package com.example.event.mapper;

import com.example.event.dto.CommentDTO;
import com.example.event.dto.CommentUserDTO;
import com.example.event.entity.Comment;
import com.example.event.entity.File;
import com.example.event.entity.User;
import com.example.event.entity.CommentReaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CommentMapper {
    private final FileMapper fileMapper;

    private CommentUserDTO toCommentUserDTO(User user) {
        if (user == null) return null;
        return CommentUserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .avatar(user.getAvatar() != null ? fileMapper.toDTO(user.getAvatar()) : null)
                .build();
    }

    public CommentDTO toDTO(
            Comment comment, 
            List<File> files, 
            long likesCount, 
            CommentReaction userReaction, 
            long repliesCount) {
        if (comment == null) return null;

        CommentDTO dto = CommentDTO.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .deletedAt(comment.getDeletedAt())
                .user(toCommentUserDTO(comment.getUser()))
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .likesCount(likesCount)
                .repliesCount(repliesCount)
                .build();

        if (files != null) {
            dto.setImages(files.stream().map(fileMapper::toDTO).collect(Collectors.toList()));
        } else {
            dto.setImages(Collections.emptyList());
        }

        if (userReaction != null) {
            dto.setLiked(true);
            dto.setCurrentReaction(userReaction.getReactionType());
        }

        return dto;
    }

    public List<CommentDTO> toDtoList(
            List<Comment> comments,
            Map<String, List<File>> filesMap,
            Map<String, Long> likesMap,
            Map<String, CommentReaction> userReactionsMap,
            Map<String, Long> repliesMap) {
        if (comments == null || comments.isEmpty()) {
            return Collections.emptyList();
        }

        return comments.stream()
                .map(comment -> {
                    List<File> files = filesMap.getOrDefault(comment.getId(), Collections.emptyList());
                    long likesCount = likesMap.getOrDefault(comment.getId(), 0L);
                    CommentReaction userReaction = userReactionsMap.get(comment.getId());
                    long repliesCount = comment.getParent() == null ? repliesMap.getOrDefault(comment.getId(), 0L) : 0L;

                    return toDTO(comment, files, likesCount, userReaction, repliesCount);
                })
                .collect(Collectors.toList());
    }
}
