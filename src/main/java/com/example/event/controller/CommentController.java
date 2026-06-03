package com.example.event.controller;

import com.example.event.dto.CommentDTO;
import com.example.event.dto.request.CommentReactionReq;
import com.example.event.dto.request.CreateCommentReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.dto.response.KeysetPageResponse;
import com.example.event.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // 1. Viết bình luận mới hoặc phản hồi
    @PostMapping
    public ResponseEntity<?> createComment(@Valid @RequestBody CreateCommentReq req) {
        CommentDTO commentDTO = commentService.createComment(req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message("Viết bình luận thành công.")
                .data(commentDTO)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    // 2. Lấy danh sách bình luận cha của sự kiện (Keyset Pagination)
    @GetMapping("/event/{eventId}")
    public ResponseEntity<?> getParentComments(
            @PathVariable String eventId,
            @RequestParam(required = false) String nextId,
            @RequestParam(defaultValue = "10") int size) {
            
        KeysetPageResponse<CommentDTO, String> response = commentService.getParentComments(eventId, nextId, size);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(response)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    // 3. Lấy danh sách bình luận trả lời (Replies) của Bình luận cha (Keyset Pagination)
    @GetMapping("/{parentId}/replies")
    public ResponseEntity<?> getReplies(
            @PathVariable String parentId,
            @RequestParam(required = false) String nextId,
            @RequestParam(defaultValue = "5") int size) {
            
        KeysetPageResponse<CommentDTO, String> response = commentService.getCommentReplies(parentId, nextId, size);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .data(response)
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    // 4. Thả cảm xúc trên Bình luận (Toggle Reaction)
    @PostMapping("/{commentId}/reactions")
    public ResponseEntity<?> toggleReaction(
            @PathVariable String commentId,
            @Valid @RequestBody CommentReactionReq req) {
            
        commentService.toggleReaction(commentId, req);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }

    // 5. Xóa bình luận (Soft Delete)
    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable String commentId) {
        commentService.deleteComment(commentId);
        ApiResponse apiResponse = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Xóa bình luận thành công.")
                .build();
        return new ResponseEntity<>(apiResponse, HttpStatus.OK);
    }
}
