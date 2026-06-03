package com.example.event.dto.request;

import com.example.event.constant.ReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentReactionReq {
    @NotNull(message = "Loại biểu tượng cảm xúc không được để trống")
    private ReactionType reactionType;
}
