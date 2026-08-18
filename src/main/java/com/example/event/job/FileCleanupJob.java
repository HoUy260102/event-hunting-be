package com.example.event.job;

import com.cloudinary.Cloudinary;
import com.example.event.entity.File;
import com.example.event.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileCleanupJob {

    private final FileRepository fileRepository;
    private final Cloudinary cloudinary;

    @Value("${app.cleanup.pending-days:7}")
    private int pendingDaysThreshold;

    @Value("${app.cleanup.deleted-days:30}")
    private int deletedDaysThreshold;

    @Value("${app.cleanup.batch-size:50}")
    private int batchSize;

    @Value("${app.cleanup.sleep-ms:500}")
    private long sleepMs;

    @Scheduled(cron = "0 0 1 * * ?")
    public void cleanupExpiredFiles() {
        log.info("Bắt đầu cleanup file hết hạn");

        LocalDateTime pendingThreshold = LocalDateTime.now().minusDays(pendingDaysThreshold);
        LocalDateTime deletedThreshold = LocalDateTime.now().minusDays(deletedDaysThreshold);

        int totalDeleted = 0;
        Pageable pageable = PageRequest.of(0, batchSize);

        while (true) {
            // 1. Query next batch from DB
            Slice<File> expiredSlice = fileRepository.findExpiredFiles(pendingThreshold, deletedThreshold, pageable);
            List<File> filesToClean = expiredSlice.getContent();

            if (filesToClean.isEmpty()) {
                break;
            }

            log.info("Tìm thấy {} file hết hạn trong batch, đang xử lý xóa Cloudinary...", filesToClean.size());

            for (File file : filesToClean) {
                if (file.getPublicId() != null && !file.getPublicId().isEmpty()) {
                    try {
                        log.info("Xóa file trên Cloudinary: fileId={}, publicId={}", file.getId(), file.getPublicId());
                        Map destroyResult = cloudinary.uploader().destroy(file.getPublicId(), new HashMap<>());
                        log.debug("Kết quả xóa Cloudinary cho {}: {}", file.getPublicId(), destroyResult);
                        if (sleepMs > 0) {
                            Thread.sleep(sleepMs);
                        }
                    } catch (IOException e) {
                        log.error("Không xóa được file trên Cloudinary (publicId={}): {}. Tiếp tục xóa khỏi DB để tránh loop.", 
                                file.getPublicId(), e.getMessage());
                    } catch (InterruptedException e) {
                        log.warn("Cleanup bị gián đoạn khi chờ: {}", e.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Lỗi khi xóa trên Cloudinary (publicId={}): {}", 
                                file.getPublicId(), e.getMessage());
                    }
                }
            }

            try {
                fileRepository.deleteAll(filesToClean);
                totalDeleted += filesToClean.size();
                log.info("Đã xóa {} file khỏi DB trong batch này.", filesToClean.size());
            } catch (Exception e) {
                log.error("Lỗi khi xóa hàng loạt file khỏi DB: {}", e.getMessage());
                for (File file : filesToClean) {
                    try {
                        fileRepository.delete(file);
                        totalDeleted++;
                    } catch (Exception ex) {
                        log.error("Không xóa được file khỏi DB (id={}): {}", file.getId(), ex.getMessage());
                    }
                }
                break;
            }
        }

        log.info("Cleanup file hết hạn hoàn tất. Tổng file đã xử lý: {}", totalDeleted);
    }
}
