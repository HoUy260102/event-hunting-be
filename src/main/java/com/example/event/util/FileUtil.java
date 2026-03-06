package com.example.event.util;

import com.example.event.constant.FileType;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

public class FileUtil {
    private static final Tika TIKA = new Tika();
    private static Map<String, List<String>> allowedFileTypes = new HashMap<>() {{
        put("IMAGE", Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
        put("VIDEO", Arrays.asList("mp4", "mov", "avi", "mkv"));
    }};
    private static Map<String, List<String>> allowedMimeTypes = new HashMap<>() {{
        put("IMAGE", Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp"));
        put("VIDEO", Arrays.asList("video/mp4", "video/x-msvideo", "video/quicktime", "video/x-matroska"));
    }};

    public static boolean isAllowedExtension(String extension, FileType fileType) {
        if (extension == null) return false;
        List<String> validFileType = FileUtil.allowedFileTypes.get(fileType.name());
        return validFileType != null && validFileType.contains(extension);
    }

    public static boolean isValidMimeType(MultipartFile file, FileType fileType) {
        try (InputStream is = file.getInputStream()) {
            String detectedType = TIKA.detect(is);
            List<String> validMimeType = FileUtil.allowedMimeTypes.get(fileType.name());
            return validMimeType != null && validMimeType.contains(detectedType);
        } catch (Exception e) {
            return false;
        }
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    public static String generatePublicId(String originalFilename) {
        String baseName = originalFilename.substring(0, originalFilename.lastIndexOf("."));
        String cleanName = baseName.replaceAll("[^a-zA-Z0-9]", "-");
        return UUID.randomUUID().toString() + "-" + cleanName;
    }
}
