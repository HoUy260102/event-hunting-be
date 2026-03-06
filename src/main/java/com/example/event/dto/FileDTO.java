package com.example.event.dto;

import com.example.event.constant.FileFolder;
import com.example.event.constant.FileStatus;
import com.example.event.constant.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileDTO {
    private String id;
    private String publicId;
    private String url;
    private FileStatus status;
    private FileType type;
    private String format;
    private FileFolder folder;
    private String referenceId;

    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
