package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.FileStatus;
import com.example.event.constant.FileFolder;
import com.example.event.dto.CommentDTO;
import com.example.event.dto.request.CommentReactionReq;
import com.example.event.dto.request.CreateCommentReq;
import com.example.event.dto.response.KeysetPageResponse;
import com.example.event.entity.*;
import com.example.event.exception.AppException;
import com.example.event.mapper.CommentMapper;
import com.example.event.repository.*;
import com.example.event.service.CommentService;
import com.example.event.service.EventInteractionService;
import com.example.event.constant.InteractionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final FileRepository fileRepository;
    private final CommentMapper commentMapper;
    private final SecurityUtils securityUtils;
    private final EventInteractionService eventInteractionService;

    @Override
    @Transactional
    public CommentDTO createComment(CreateCommentReq req) {
        String userId = securityUtils.getCurrentUserId();
        log.info("createComment - Start: userId={}, req={}", userId, req);

        try {
            if (userId == null) {
                log.warn("createComment - Unauthenticated request");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            User user = userRepository.findUserByIdWithAvatar(userId);
            if (user == null) {
                log.warn("createComment - User not found: userId={}", userId);
                throw new AppException(ErrorCode.USER_NOT_FOUND);
            }

            Event event = eventRepository.findById(req.getEventId())
                    .orElseThrow(() -> {
                        log.warn("createComment - Event not found: eventId={}", req.getEventId());
                        return new AppException(ErrorCode.EVENT_NOT_FOUND);
                    });

            // 1. Kiểm tra giới hạn số lượng ảnh đính kèm (Tối đa 4 ảnh)
            final int MAX_IMAGES = 4;
            if (req.getFileIds() != null && req.getFileIds().size() > MAX_IMAGES) {
                log.warn("createComment - Image limit exceeded: size={}", req.getFileIds().size());
                throw new AppException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
            }

            // Kiểm tra: Phải có ít nhất nội dung chữ HOẶC ảnh đính kèm
            boolean hasText = req.getContent() != null && !req.getContent().trim().isEmpty();
            boolean hasImages = req.getFileIds() != null && !req.getFileIds().isEmpty();
            if (!hasText && !hasImages) {
                log.warn("createComment - Comment has neither text nor images");
                throw new AppException(ErrorCode.COMMENT_EMPTY);
            }

            Comment comment = new Comment();
            comment.setUser(user);
            comment.setEvent(event);
            comment.setContent(hasText ? req.getContent().trim() : "");

            // 2. Kiểm tra điều kiện giới hạn 2 bậc bình luận
            if (req.getParentId() != null) {
                Comment parentComment = commentRepository.findById(req.getParentId())
                        .orElseThrow(() -> {
                            log.warn("createComment - Parent comment not found: parentId={}", req.getParentId());
                            return new AppException(ErrorCode.COMMENT_NOT_FOUND);
                        });

                if (parentComment.getParent() != null) {
                    log.warn("createComment - Comment level exceeded for parentId={}", req.getParentId());
                    throw new AppException(ErrorCode.COMMENT_LEVEL_EXCEEDED);
                }
                comment.setParent(parentComment);
            }

            // 3. Lưu bình luận
            Comment savedComment = commentRepository.save(comment);
            log.info("createComment - Comment entity saved: commentId={}", savedComment.getId());

            // Ghi nhận tương tác Event Interaction (Tăng điểm gợi ý/xu hướng)
            eventInteractionService.addInteraction(event.getId(), userId, InteractionType.COMMENT);

            // 4. Kích hoạt và liên kết file ảnh đính kèm (chuyển PENDING sang ACTIVE)
            if (req.getFileIds() != null && !req.getFileIds().isEmpty()) {
                log.info("createComment - Activating files: fileIds={}", req.getFileIds());
                fileRepository.activateFiles(req.getFileIds(), savedComment.getId());
            }

            List<File> activeFiles = fileRepository.findFilesByStatusAndReferenceId(FileStatus.ACTIVE, savedComment.getId());
            CommentDTO result = commentMapper.toDTO(savedComment, activeFiles, 0L, null, 0L);
            log.info("createComment - Success! Result DTO built: commentId={}", result.getId());
            return result;
        } catch (Exception e) {
            log.error("createComment - Unexpected exception occurred during comment creation: ", e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public KeysetPageResponse<CommentDTO, String> getParentComments(String eventId, String nextId, int size) {
        if (!eventRepository.existsById(eventId)) {
            throw new AppException(ErrorCode.EVENT_NOT_FOUND);
        }

        String currentUserId = securityUtils.getCurrentUserId(); // Lấy nếu có (không bắt buộc đăng nhập để xem)

        Pageable pageable = PageRequest.of(0, size);
        Slice<Comment> parentSlice = commentRepository.findParentCommentsKeyset(eventId, nextId, pageable);

        List<CommentDTO> dtos = mapCommentsToDTOs(parentSlice.getContent(), currentUserId);

        String nextKeysetId = (parentSlice.hasNext() && !dtos.isEmpty())
                ? dtos.get(dtos.size() - 1).getId()
                : null;

        return new KeysetPageResponse<>(dtos, nextKeysetId, parentSlice.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public KeysetPageResponse<CommentDTO, String> getCommentReplies(String parentId, String nextId, int size) {
        if (!commentRepository.existsById(parentId)) {
            throw new AppException(ErrorCode.COMMENT_NOT_FOUND);
        }

        String currentUserId = securityUtils.getCurrentUserId(); // Lấy nếu có

        Pageable pageable = PageRequest.of(0, size);
        Slice<Comment> replySlice = commentRepository.findRepliesKeyset(parentId, nextId, pageable);

        List<CommentDTO> dtos = mapCommentsToDTOs(replySlice.getContent(), currentUserId);

        String nextKeysetId = (replySlice.hasNext() && !dtos.isEmpty())
                ? dtos.get(dtos.size() - 1).getId()
                : null;

        return new KeysetPageResponse<>(dtos, nextKeysetId, replySlice.hasNext());
    }

    @Override
    @Transactional
    public void toggleReaction(String commentId, CommentReactionReq req) {
        String userId = securityUtils.getCurrentUserId();
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));

        User user = userRepository.findUserByIdWithAvatar(userId);
        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        commentReactionRepository.findByCommentIdAndUserId(commentId, userId)
                .ifPresentOrElse(
                        existingReaction -> {
                            // Nếu trùng cảm xúc -> Bỏ thả (Toggle Off)
                            if (existingReaction.getReactionType() == req.getReactionType()) {
                                commentReactionRepository.delete(existingReaction);
                            } else {
                                // Nếu khác cảm xúc -> Cập nhật cảm xúc mới
                                existingReaction.setReactionType(req.getReactionType());
                                commentReactionRepository.save(existingReaction);
                            }
                        },
                        () -> {
                            // Chưa có cảm xúc -> Thêm mới cảm xúc
                            CommentReaction newReaction = new CommentReaction();
                            newReaction.setComment(comment);
                            newReaction.setUser(user);
                            newReaction.setReactionType(req.getReactionType());
                            commentReactionRepository.save(newReaction);
                        }
                );
    }

    @Override
    @Transactional
    public void deleteComment(String commentId) {
        String userId = securityUtils.getCurrentUserId();
        log.info("deleteComment - Start: userId={}, commentId={}", userId, commentId);

        try {
            if (userId == null) {
                log.warn("deleteComment - Unauthenticated request");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Comment comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> {
                        log.warn("deleteComment - Comment not found: commentId={}", commentId);
                        return new AppException(ErrorCode.COMMENT_NOT_FOUND);
                    });

            // Bảo mật: Chỉ cho phép chính chủ nhân hoặc Admin xóa bình luận
            securityUtils.canAccessThisResource(comment.getUser().getId());

            // 1. Thực hiện xóa mềm bình luận (Soft Delete)
            comment.setDeletedAt(LocalDateTime.now());
            commentRepository.save(comment);
            log.info("deleteComment - Comment soft-deleted successfully: commentId={}", commentId);

            // 2. Thực hiện xóa mềm toàn bộ ảnh đính kèm của bình luận đó (nếu có)
            List<File> commentFiles = fileRepository.findFilesByStatusAndReferenceId(FileStatus.ACTIVE, commentId);
            if (commentFiles != null && !commentFiles.isEmpty()) {
                log.info("deleteComment - Soft-deleting {} attached files for commentId={}", commentFiles.size(), commentId);
                List<String> fileIds = commentFiles.stream().map(File::getId).toList();
                fileRepository.deleteFile(fileIds, LocalDateTime.now());
            }

            log.info("deleteComment - Success: commentId={}", commentId);
        } catch (Exception e) {
            log.error("deleteComment - Unexpected exception during comment deletion: ", e);
            throw e;
        }
    }

    private List<CommentDTO> mapCommentsToDTOs(List<Comment> comments, String currentUserId) {
        if (comments == null || comments.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<String> commentIds = comments.stream()
                .map(Comment::getId)
                .collect(java.util.stream.Collectors.toList());

        // 1. Bulk files mapping
        List<File> allFiles = fileRepository.findFilesByStatusAndReferenceIds(FileStatus.ACTIVE, commentIds);
        java.util.Map<String, List<File>> filesMap = allFiles.stream()
                .filter(f -> f.getReferenceId() != null)
                .collect(java.util.stream.Collectors.groupingBy(File::getReferenceId));

        // 2. Bulk likes count mapping
        List<Object[]> likesCountResults = commentReactionRepository.countLikesForCommentIds(commentIds);
        java.util.Map<String, Long> likesMap = likesCountResults.stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        // 3. Bulk current user reaction mapping
        java.util.Map<String, CommentReaction> userReactionsMap = new java.util.HashMap<>();
        if (currentUserId != null) {
            List<CommentReaction> userReactions = commentReactionRepository.findReactionsByCommentIdsAndUserId(commentIds, currentUserId);
            userReactionsMap = userReactions.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            r -> r.getComment().getId(),
                            r -> r
                    ));
        }

        // 4. Bulk replies count mapping
        List<Object[]> repliesCountResults = commentRepository.countRepliesForParentIds(commentIds);
        java.util.Map<String, Long> repliesMap = repliesCountResults.stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        return commentMapper.toDtoList(comments, filesMap, likesMap, userReactionsMap, repliesMap);
    }
}
