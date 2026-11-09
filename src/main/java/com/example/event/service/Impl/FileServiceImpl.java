package com.example.event.service.Impl;

import com.cloudinary.Cloudinary;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.FileFolder;
import com.example.event.constant.FileStatus;
import com.example.event.constant.FileType;
import com.example.event.dto.FileDTO;
import com.example.event.dto.request.CompleteUploadReq;
import com.example.event.dto.request.InitUploadReq;
import com.example.event.dto.response.UploadSignatureResponse;
import com.example.event.entity.File;
import com.example.event.exception.AppException;
import com.example.event.mapper.FileMapper;
import com.example.event.repository.FileRepository;
import com.example.event.service.FileService;
import com.example.event.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024; // 10MB cho Cloudinary Free Image
    private static final long MAX_VIDEO_SIZE = 100L * 1024 * 1024; // 100MB cho Cloudinary Free Video

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

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
            log.warn("[FILE] Đuôi file không hợp lệ: filename={}, extension={}, expectedType={}",
                    file.getOriginalFilename(), fileExtension, fileType);
            throw new AppException(ErrorCode.INVALID_FILE_EXTENSION);
        }
        if (!FileUtil.isValidMimeType(file, fileType)) {
            log.warn("[FILE] MIME type không hợp lệ: filename={}, contentType={}, expectedType={}",
                    file.getOriginalFilename(), file.getContentType(), fileType);
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
            savedFile.setStatus(FileStatus.UPLOADED);
            savedFile.setSize(file.getSize());
            savedFile.setCreatedAt(LocalDateTime.now());

            Object formatObj = response.get("format");
            savedFile.setFormat(formatObj != null ? formatObj.toString() : fileExtension);
            fileRepository.save(savedFile);
            log.info("[FILE] Tải file lên Cloudinary và lưu DB thành công: fileId={}, publicId={}, folder={}",
                    savedFile.getId(), savedFile.getPublicId(), fileFolder);
            return fileMapper.toDTO(savedFile);
        } catch (IOException e) {
            log.error("[FILE] Lỗi khi upload file lên Cloudinary: filename={}, error={}",
                    file.getOriginalFilename(), e.getMessage(), e);
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }

    @Override
    public Map<String, Object> generateUploadSignature() {
        long timestamp = System.currentTimeMillis() / 1000L;

        Map<String, Object> params = new HashMap<>();
        String folder = "eventhunting/test";
        params.put("timestamp", timestamp);
        params.put("folder", folder);
        String signature = cloudinary.apiSignRequest(params, cloudinary.config.apiSecret);

        Map<String, Object> response = new HashMap<>();
        response.put("signature", signature);
        response.put("timestamp", timestamp);
        response.put("folder", folder);
        response.put("api_key", cloudinary.config.apiKey);
        response.put("cloud_name", cloudinary.config.cloudName);
        log.info("[FILE] Tạo chữ ký upload trực tiếp lên Cloudinary thành công | folder={}, timestamp={}", folder,
                timestamp);
        return response;
    }

    @Override
    @Transactional
    public UploadSignatureResponse generateUploadSignature(InitUploadReq req) {
        // 1. Kiểm tra kích thước file
        if (req.getFileSize() == null || req.getFileSize() <= 0) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }
        long maxAllowed = (req.getFileType() == FileType.VIDEO) ? MAX_VIDEO_SIZE : MAX_IMAGE_SIZE;
        if (req.getFileSize() > maxAllowed) {
            log.warn("[FILE] File vượt quá dung lượng cho phép: size={}, maxAllowed={}, type={}",
                    req.getFileSize(), maxAllowed, req.getFileType());
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        // 2. Kiểm tra Extension
        String ext = FileUtil.getFileExtension(req.getFileName());
        if (!FileUtil.isAllowedExtension(ext, req.getFileType())) {
            log.warn("[FILE] Đuôi file không hợp lệ: filename={}, extension={}, expectedType={}",
                    req.getFileName(), ext, req.getFileType());
            throw new AppException(ErrorCode.INVALID_FILE_EXTENSION);
        }

        // 3. Kiểm tra Magic Bytes từ 4KB đầu qua Apache Tika
        if (req.getHeaderBytes() == null || req.getHeaderBytes().isBlank()) {
            throw new AppException(ErrorCode.INVALID_FILE_MIME_TYPE);
        }
        byte[] headerBytes;
        try {
            headerBytes = Base64.getDecoder().decode(req.getHeaderBytes().trim());
        } catch (IllegalArgumentException e) {
            log.warn("[FILE] Base64 headerBytes không hợp lệ: {}", e.getMessage());
            throw new AppException(ErrorCode.INVALID_FILE_MIME_TYPE);
        }

        String detectedMime = FileUtil.detectMimeType(headerBytes);
        if (!FileUtil.isAllowedMimeType(detectedMime, req.getFileType())) {
            log.warn(
                    "[SECURITY] Phát hiện MIME type không khớp Magic Bytes: filename={}, detectedMime={}, expectedType={}",
                    req.getFileName(), detectedMime, req.getFileType());
            throw new AppException(ErrorCode.INVALID_FILE_MIME_TYPE);
        }

        // 4. Chuẩn bị thông tin folder và publicId
        String cleanPublicId = FileUtil.generatePublicId(req.getFileName());
        String targetFolder = "eventhunting/" + req.getFolder().name().toLowerCase();
        String fullPublicId = targetFolder + "/" + cleanPublicId;
        String resourceType = (req.getFileType() == FileType.VIDEO) ? "video" : "image";
        String allowedFormats = FileUtil.getAllowedFormatsString(req.getFileType());

        // Tạo trước bản ghi File ở trạng thái PENDING
        File savedFile = new File();
        savedFile.setPublicId(fullPublicId);
        savedFile.setFolder(req.getFolder());
        savedFile.setType(req.getFileType());
        savedFile.setStatus(FileStatus.PENDING);
        savedFile.setFormat(ext);
        savedFile.setCreatedAt(LocalDateTime.now());

        // Sinh link tạm tính của Cloudinary
        String fallbackUrl = cloudinary.url()
                .secure(true)
                .resourceType(resourceType)
                .generate(fullPublicId + "." + ext);
        savedFile.setUrl(fallbackUrl);

        fileRepository.save(savedFile);
        log.info("[FILE] Đã tạo bản ghi File PENDING: fileId={}, publicId={}", savedFile.getId(), fullPublicId);

        // 5. Ký chữ ký Cloudinary
        long timestamp = System.currentTimeMillis() / 1000L;
        Map<String, Object> params = new HashMap<>();
        params.put("timestamp", timestamp);
        params.put("folder", targetFolder);
        params.put("public_id", cleanPublicId);
        if (allowedFormats != null && !allowedFormats.isBlank()) {
            params.put("allowed_formats", allowedFormats);
        }

        String signature = cloudinary.apiSignRequest(params, cloudinary.config.apiSecret);

        return UploadSignatureResponse.builder()
                .fileId(savedFile.getId())
                .publicId(cleanPublicId)
                .signature(signature)
                .timestamp(timestamp)
                .apiKey(cloudinary.config.apiKey)
                .cloudName(cloudinary.config.cloudName)
                .folder(targetFolder)
                .allowedFormats(allowedFormats)
                .resourceType(resourceType)
                .build();
    }

    @Override
    @Transactional
    public FileDTO completeUpload(CompleteUploadReq req) {
        File file = fileRepository.findById(req.getFileId())
                .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));

        if (file.getStatus() == FileStatus.DELETED) {
            log.warn("[FILE] File đã bị xóa, không thể hoàn tất upload: fileId={}", file.getId());
            throw new AppException(ErrorCode.FILE_NOT_FOUND);
        }

        if (file.getStatus() == FileStatus.ACTIVE) {
            log.info("[FILE] File đã ở trạng thái ACTIVE, giữ nguyên: fileId={}", file.getId());
            return fileMapper.toDTO(file);
        }

        if (req.getUrl() == null || req.getUrl().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "URL không được để trống.");
        }

        String targetUrl = req.getUrl().trim();

        // 1. Kiểm tra URL hợp lệ & SSRF protection
        try {
            URI uri = URI.create(targetUrl);
            String host = uri.getHost();
            if (host == null || (!host.equalsIgnoreCase("res.cloudinary.com") && !host.endsWith(".cloudinary.com"))) {
                log.warn("[SECURITY] Phát hiện URL không thuộc Cloudinary: url={}", targetUrl);
                throw new AppException(ErrorCode.VALIDATION_ERROR, "URL file không hợp lệ.");
            }
        } catch (IllegalArgumentException e) {
            log.warn("[SECURITY] URL không đúng định dạng: url={}", targetUrl);
            throw new AppException(ErrorCode.VALIDATION_ERROR, "URL file không đúng định dạng.");
        }

        // Đảm bảo URL chứa đúng publicId của file đã khởi tạo
        if (file.getPublicId() != null && !targetUrl.contains(file.getPublicId())) {
            log.warn("[SECURITY] URL không khớp publicId đã khởi tạo: expectedPublicId={}, url={}",
                    file.getPublicId(), targetUrl);
            throw new AppException(ErrorCode.VALIDATION_ERROR, "URL file không khớp với bản ghi đã khởi tạo.");
        }

        // 2. Lấy dung lượng thực tế trên Cloudinary bằng HTTP HEAD request
        long actualSize = fetchActualFileSize(targetUrl);
        long maxAllowed = (file.getType() == FileType.VIDEO) ? MAX_VIDEO_SIZE : MAX_IMAGE_SIZE;

        if (actualSize > maxAllowed) {
            log.warn(
                    "[SECURITY] Dung lượng file thực tế vượt quá trần cho phép: actualSize={}, maxAllowed={}, publicId={}",
                    actualSize, maxAllowed, file.getPublicId());
            destroyCloudinaryFileSilently(file.getPublicId(), file.getType());
            fileRepository.delete(file);
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        file.setUrl(targetUrl);
        file.setStatus(FileStatus.UPLOADED);
        if (actualSize > 0) {
            file.setSize(actualSize);
        }
        fileRepository.save(file);
        log.info(
                "[FILE] Hoàn tất cập nhật secure_url, size={} bytes và chuyển status sang UPLOADED cho fileId={}, url={}",
                file.getSize(), file.getId(), file.getUrl());

        return fileMapper.toDTO(file);
    }

    private long fetchActualFileSize(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                return response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            } else {
                log.warn("[FILE] HTTP HEAD tới Cloudinary trả về status không thành công: status={}, url={}",
                        response.statusCode(), url);
            }
        } catch (Exception e) {
            log.warn("[FILE] Không thể thực hiện HTTP HEAD để kiểm tra dung lượng: url={}, error={}",
                    url, e.getMessage());
        }
        return -1L;
    }

    private void destroyCloudinaryFileSilently(String publicId, FileType fileType) {
        if (publicId == null || publicId.isBlank())
            return;
        try {
            Map<String, Object> destroyOptions = new HashMap<>();
            if (fileType == FileType.VIDEO) {
                destroyOptions.put("resource_type", "video");
            }
            cloudinary.uploader().destroy(publicId, destroyOptions);
        } catch (Exception e) {
            log.error("[FILE] Lỗi khi hủy file vượt dung lượng trên Cloudinary: publicId={}, error={}",
                    publicId, e.getMessage());
        }
    }
}
