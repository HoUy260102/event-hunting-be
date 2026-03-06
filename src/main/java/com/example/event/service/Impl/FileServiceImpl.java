package com.example.event.service.Impl;

import com.cloudinary.Cloudinary;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.FileFolder;
import com.example.event.constant.FileStatus;
import com.example.event.constant.FileType;
import com.example.event.dto.FileDTO;
import com.example.event.entity.File;
import com.example.event.exception.AppException;
import com.example.event.mapper.FileMapper;
import com.example.event.repository.FileRepository;
import com.example.event.service.FileService;
import com.example.event.util.FileUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    private final Cloudinary cloudinary;
    private final FileRepository fileRepository;
    private final FileMapper fileMapper;

    private Map uploadBaseFile(MultipartFile file, Map<String, Object> options) throws IOException {
        if (options == null) {
            options = new HashMap<>();
        }
        return cloudinary.uploader().upload(file.getBytes(), options);
    }

    @Override
    @Transactional
    public FileDTO uploadFile(MultipartFile file, FileType fileType, FileFolder fileFolder) {
        String fileExtension = FileUtil.getFileExtension(file.getOriginalFilename());
        if (!FileUtil.isAllowedExtension(fileExtension, fileType)) {
            throw new AppException(ErrorCode.INVALID_FILE_EXTENSION);
        }
        if (!FileUtil.isValidMimeType(file, fileType)) {
            throw new AppException(ErrorCode.INVALID_FILE_MIME_TYPE);
        }
        Map<String, Object> options = new HashMap<>();
        String publicId = FileUtil.generatePublicId(file.getOriginalFilename());
        options.put("folder", "/eventhunting/" + fileFolder.name().toLowerCase());
        options.put("resource_type", "auto");
        options.put("public_id", publicId);
        try {
            Map response = uploadBaseFile(file, options);
            File savedFile = new File();
            savedFile.setPublicId(response.get("public_id").toString());
            savedFile.setUrl(response.get("secure_url").toString());
            savedFile.setFolder(fileFolder);
            savedFile.setType(fileType);
            savedFile.setStatus(FileStatus.PENDING);
            savedFile.setCreatedAt(LocalDateTime.now());

            Object formatObj = response.get("format");
            savedFile.setFormat(formatObj != null ? formatObj.toString() : fileExtension);
            fileRepository.save(savedFile);
            return fileMapper.toDTO(savedFile);
        } catch (IOException e) {
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }
}
