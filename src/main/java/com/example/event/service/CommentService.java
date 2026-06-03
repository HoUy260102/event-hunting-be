package com.example.event.service;

import com.example.event.dto.CommentDTO;
import com.example.event.dto.request.CommentReactionReq;
import com.example.event.dto.request.CreateCommentReq;
import com.example.event.dto.response.KeysetPageResponse;

public interface CommentService {
    CommentDTO createComment(CreateCommentReq req);
    KeysetPageResponse<CommentDTO, String> getParentComments(String eventId, String nextId, int size);
    KeysetPageResponse<CommentDTO, String> getCommentReplies(String parentId, String nextId, int size);
    void toggleReaction(String commentId, CommentReactionReq req);
    void deleteComment(String commentId);
}
