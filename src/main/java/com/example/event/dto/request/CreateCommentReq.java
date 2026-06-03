package com.example.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentReq {
    @NotBlank(message = "ID sự kiện không được để trống")
    private String eventId;

    private String parentId;

    private String content;

    private List<String> fileIds;
}
