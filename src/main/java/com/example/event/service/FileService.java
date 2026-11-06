package com.example.event.service;

import com.example.event.constant.FileFolder;
import com.example.event.constant.FileType;
import com.example.event.dto.FileDTO;
import com.example.event.dto.request.CompleteUploadReq;
import com.example.event.dto.request.InitUploadReq;
import com.example.event.dto.response.UploadSignatureResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface FileService {
    FileDTO uploadFile(MultipartFile file, FileType fileType, FileFolder fileFolder);
    Map<String, Object> generateUploadSignature();
    UploadSignatureResponse generateUploadSignature(InitUploadReq req);
    FileDTO completeUpload(CompleteUploadReq req);
}
