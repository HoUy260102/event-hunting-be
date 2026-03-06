package com.example.event.service;

import com.example.event.constant.FileFolder;
import com.example.event.constant.FileType;
import com.example.event.dto.FileDTO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    FileDTO uploadFile(MultipartFile file, FileType fileType, FileFolder fileFolder);
}
