package com.example.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompleteUploadReq {
    @NotBlank(message = "FileId không được để trống")
    private String fileId;

    @NotBlank(message = "URL không được để trống")
    private String url;
}

