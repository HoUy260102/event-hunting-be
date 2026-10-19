package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.FileStatus;
import com.example.event.constant.FileFolder;
import com.example.event.dto.CommentDTO;
import com.example.event.dto.request.CommentReactionReq;
import com.example.event.dto.request.CreateCommentReq;
import com.example.event.dto.request.UpdateCommentReq;
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
        log.info("[COMMENT] Bắt đầu tạo bình luận: userId={}, req={}", userId, req);

        try {
            if (userId == null) {
                log.warn("[COMMENT] Từ chối tạo bình luận: Người dùng chưa xác thực");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            User user = userRepository.findUserByIdWithAvatar(userId);
            if (user == null) {
                log.warn("[COMMENT] Không tìm thấy người dùng: userId={}", userId);
                throw new AppException(ErrorCode.USER_NOT_FOUND);
            }

            Event event = eventRepository.findById(req.getEventId())
                    .orElseThrow(() -> {
                        log.warn("[COMMENT] Không tìm thấy sự kiện: eventId={}", req.getEventId());
                        return new AppException(ErrorCode.EVENT_NOT_FOUND);
                    });

            // 1. Kiểm tra giới hạn số lượng ảnh đính kèm (Tối đa 4 ảnh)
            final int MAX_IMAGES = 4;
            if (req.getFileIds() != null && req.getFileIds().size() > MAX_IMAGES) {
                log.warn("[COMMENT] Vượt quá giới hạn ảnh đính kèm: size={}", req.getFileIds().size());
                throw new AppException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
            }

            // Kiểm tra: Phải có ít nhất nội dung chữ HOẶC ảnh đính kèm
            boolean hasText = req.getContent() != null && !req.getContent().trim().isEmpty();
            boolean hasImages = req.getFileIds() != null && !req.getFileIds().isEmpty();
            if (!hasText && !hasImages) {
                log.warn("[COMMENT] Bình luận không có nội dung chữ hoặc ảnh");
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
                            log.warn("[COMMENT] Không tìm thấy bình luận cha: parentId={}", req.getParentId());
                            return new AppException(ErrorCode.COMMENT_NOT_FOUND);
                        });

                if (parentComment.getParent() != null) {
                    log.warn("[COMMENT] Vượt quá cấp độ bình luận cho parentId={}", req.getParentId());
                    throw new AppException(ErrorCode.COMMENT_LEVEL_EXCEEDED);
                }
                comment.setParent(parentComment);
            }

            // 3. Lưu bình luận
            Comment savedComment = commentRepository.save(comment);
            log.info("[COMMENT] Đã lưu entity bình luận: commentId={}", savedComment.getId());

            // Ghi nhận tương tác Event Interaction (Tăng điểm gợi ý/xu hướng)
            eventInteractionService.addInteraction(event.getId(), userId, InteractionType.COMMENT);

            // 4. Kích hoạt và liên kết file ảnh đính kèm (chuyển PENDING sang ACTIVE)
            if (req.getFileIds() != null && !req.getFileIds().isEmpty()) {
                log.info("[COMMENT] Kích hoạt các file đính kèm: fileIds={}", req.getFileIds());
                fileRepository.activateFiles(req.getFileIds(), savedComment.getId());
            }

            List<File> activeFiles = fileRepository.findFilesByStatusAndReferenceId(FileStatus.ACTIVE, savedComment.getId());
            CommentDTO result = commentMapper.toDTO(savedComment, activeFiles, 0L, null, 0L);
            log.info("[COMMENT] Tạo bình luận thành công: commentId={}", result.getId());
            return result;
        } catch (Exception e) {
            log.error("[COMMENT] Lỗi không xác định khi tạo bình luận: ", e);
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
        log.info("[COMMENT] Bắt đầu xóa bình luận: userId={}, commentId={}", userId, commentId);

        try {
            if (userId == null) {
                log.warn("[COMMENT] Từ chối xóa bình luận: Người dùng chưa xác thực");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Comment comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> {
                        log.warn("[COMMENT] Không tìm thấy bình luận cần xóa: commentId={}", commentId);
                        return new AppException(ErrorCode.COMMENT_NOT_FOUND);
                    });

            // Bảo mật: Chỉ cho phép chính chủ nhân hoặc Admin xóa bình luận
            securityUtils.canAccessThisResource(comment.getUser().getId());

            // 1. Thực hiện xóa mềm bình luận (Soft Delete)
            comment.setDeletedAt(LocalDateTime.now());
            commentRepository.save(comment);
            log.info("[COMMENT] Đã xóa mềm bình luận: commentId={}", commentId);

            // 2. Thực hiện xóa mềm toàn bộ ảnh đính kèm của bình luận đó (nếu có)
            List<File> commentFiles = fileRepository.findFilesByStatusAndReferenceId(FileStatus.ACTIVE, commentId);
            if (commentFiles != null && !commentFiles.isEmpty()) {
                log.info("[COMMENT] Xóa mềm {} file ảnh đính kèm của commentId={}", commentFiles.size(), commentId);
                List<String> fileIds = commentFiles.stream().map(File::getId).toList();
                fileRepository.deleteFile(fileIds, LocalDateTime.now());
            }

            log.info("[COMMENT] Xóa bình luận thành công: commentId={}", commentId);
        } catch (Exception e) {
            log.error("[COMMENT] Lỗi không xác định khi xóa bình luận: ", e);
            throw e;
        }
    }

    @Override
    @Transactional
    public CommentDTO updateComment(String commentId, UpdateCommentReq req) {
        String userId = securityUtils.getCurrentUserId();
        log.info("[COMMENT] Bắt đầu chỉnh sửa bình luận: commentId={}, userId={}, req={}", commentId, userId, req);

        try {
            if (userId == null) {
                log.warn("[COMMENT] Từ chối chỉnh sửa: Người dùng chưa xác thực");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Comment comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> {
                        log.warn("[COMMENT] Không tìm thấy bình luận: commentId={}", commentId);
                        return new AppException(ErrorCode.COMMENT_NOT_FOUND);
                    });

            if (comment.getDeletedAt() != null) {
                log.warn("[COMMENT] Bình luận đã bị xóa: commentId={}", commentId);
                throw new AppException(ErrorCode.COMMENT_NOT_FOUND);
            }

            // Quyền truy cập: Chỉ chính chủ nhân được sửa bình luận
            securityUtils.canAccessThisResource(comment.getUser().getId());

            // 1. Kiểm tra giới hạn số lượng ảnh đính kèm (Tối đa 4 ảnh)
            final int MAX_IMAGES = 4;
            List<String> newFileIds = req.getFileIds() != null ? req.getFileIds() : new java.util.ArrayList<>();
            if (newFileIds.size() > MAX_IMAGES) {
                log.warn("[COMMENT] Vượt quá giới hạn ảnh đính kèm: size={}", newFileIds.size());
                throw new AppException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
            }

            // 2. Kiểm tra không được để trống cả chữ lẫn ảnh
            boolean hasText = req.getContent() != null && !req.getContent().trim().isEmpty();
            boolean hasImages = !newFileIds.isEmpty();
            if (!hasText && !hasImages) {
                log.warn("[COMMENT] Bình luận chỉnh sửa không có nội dung chữ hoặc ảnh");
                throw new AppException(ErrorCode.COMMENT_EMPTY);
            }

            // 3. Diff ảnh: Lấy danh sách ảnh ACTIVE hiện tại của comment
            List<File> currentFiles = fileRepository.findFilesByStatusAndReferenceId(FileStatus.ACTIVE, commentId);
            List<String> oldFileIds = currentFiles != null
                    ? currentFiles.stream().map(File::getId).collect(java.util.stream.Collectors.toList())
                    : new java.util.ArrayList<>();

            List<String> fileIdsToActivate = extractIdsToUpdate(oldFileIds, newFileIds);
            List<String> fileIdsToDelete = extractIdsToDelete(oldFileIds, newFileIds);

            // Kích hoạt các ảnh mới được thêm vào (từ PENDING sang ACTIVE)
            if (!fileIdsToActivate.isEmpty()) {
                log.info("[COMMENT] Kích hoạt file ảnh mới: fileIds={}", fileIdsToActivate);
                fileRepository.activateFiles(fileIdsToActivate, comment.getId());
            }

            // Xóa mềm các ảnh bị gỡ bỏ (từ ACTIVE sang DELETED)
            if (!fileIdsToDelete.isEmpty()) {
                log.info("[COMMENT] Xóa mềm file ảnh bị gỡ: fileIds={}", fileIdsToDelete);
                fileRepository.deleteFile(fileIdsToDelete, LocalDateTime.now());
            }

            // 4. Cập nhật nội dung và thời gian sửa
            comment.setContent(hasText ? req.getContent().trim() : "");
            comment.setUpdatedAt(LocalDateTime.now());
            Comment savedComment = commentRepository.save(comment);

            // 5. Lấy danh sách file active mới nhất sau diff
            List<File> updatedFiles = fileRepository.findFilesByStatusAndReferenceId(FileStatus.ACTIVE, comment.getId());

            // Lấy thông tin reaction và replies hiện tại để map DTO đầy đủ
            long likesCount = commentReactionRepository.countByCommentId(commentId);
            CommentReaction userReaction = commentReactionRepository
                    .findByCommentIdAndUserId(commentId, userId)
                    .orElse(null);
            long repliesCount = comment.getParent() == null
                    ? commentRepository.countByParentIdAndDeletedAtIsNull(commentId)
                    : 0L;

            CommentDTO result = commentMapper.toDTO(savedComment, updatedFiles, likesCount, userReaction, repliesCount);
            log.info("[COMMENT] Chỉnh sửa bình luận thành công: commentId={}", result.getId());
            return result;
        } catch (Exception e) {
            log.error("[COMMENT] Lỗi không xác định khi chỉnh sửa bình luận: ", e);
            throw e;
        }
    }

    private List<String> extractIdsToUpdate(List<String> oldIds, List<String> newIds) {
        List<String> idsForUpdate = new java.util.ArrayList<>();
        java.util.Set<String> markedIds = new java.util.HashSet<>(oldIds);
        for (String id : newIds) {
            if (!markedIds.contains(id)) {
                idsForUpdate.add(id);
            }
        }
        return idsForUpdate;
    }

    private List<String> extractIdsToDelete(List<String> oldIds, List<String> newIds) {
        List<String> idsForDelete = new java.util.ArrayList<>();
        java.util.Set<String> markedIds = new java.util.HashSet<>(newIds);
        for (String id : oldIds) {
            if (!markedIds.contains(id)) {
                idsForDelete.add(id);
            }
        }
        return idsForDelete;
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
