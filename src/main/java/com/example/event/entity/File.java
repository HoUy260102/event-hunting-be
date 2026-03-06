package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import com.example.event.constant.FileFolder;
import com.example.event.constant.FileStatus;
import com.example.event.constant.FileType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class File {
    @Id
    @UlidID
    private String id;
    private String publicId;
    @jakarta.persistence.Column(length = 500)
    private String url;
    @Enumerated(EnumType.STRING)
    private FileStatus status;
    @Enumerated(EnumType.STRING)
    private FileType type;
    private String format;
    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(name = "folder", length = 50)
    private FileFolder folder;
    private String referenceId;

    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
