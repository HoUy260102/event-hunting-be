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

    /**
     * Cron job running every day at 1:00 AM to clean up:
     * 1. Files in PENDING status older than X days.
     * 2. Files in DELETED status (soft-deleted) older than X days.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void cleanupExpiredFiles() {
        log.info("Starting expired files cleanup job task...");

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

            log.info("Found {} expired files in the current batch. Processing Cloudinary deletion...", filesToClean.size());

            // 2. Loop and delete from Cloudinary first (outside active DB transaction)
            for (File file : filesToClean) {
                if (file.getPublicId() != null && !file.getPublicId().isEmpty()) {
                    try {
                        log.info("Deleting file from Cloudinary: fileId={}, publicId={}", file.getId(), file.getPublicId());
                        Map destroyResult = cloudinary.uploader().destroy(file.getPublicId(), new HashMap<>());
                        log.debug("Cloudinary destruction result for {}: {}", file.getPublicId(), destroyResult);

                        // Pause between deletions to respect Cloudinary API rate limits
                        if (sleepMs > 0) {
                            Thread.sleep(sleepMs);
                        }
                    } catch (IOException e) {
                        log.error("Failed to delete file from Cloudinary (publicId={}): {}. Proceeding to delete from DB to prevent infinite loop.", 
                                file.getPublicId(), e.getMessage());
                    } catch (InterruptedException e) {
                        log.warn("File cleanup job interrupted during sleep: {}", e.getMessage());
                        Thread.currentThread().interrupt(); // Restore interrupted status flag
                        break; // Exit loop on interruption request
                    } catch (Exception e) {
                        log.error("Unexpected error deleting from Cloudinary (publicId={}): {}", 
                                file.getPublicId(), e.getMessage());
                    }
                }
            }

            // 3. Perform high-performance bulk database deletion
            try {
                fileRepository.deleteAll(filesToClean);
                totalDeleted += filesToClean.size();
                log.info("Successfully cleaned batch of {} files from database.", filesToClean.size());
            } catch (Exception e) {
                log.error("Error performing bulk database deletion for current batch: {}", e.getMessage());
                // Fallback to individual deletions to ensure progress if there is a specific DB constraint error
                for (File file : filesToClean) {
                    try {
                        fileRepository.delete(file);
                        totalDeleted++;
                    } catch (Exception ex) {
                        log.error("Failed to delete file from DB individually (id={}): {}", file.getId(), ex.getMessage());
                    }
                }
                break; // Stop to prevent potential infinite loops on DB failure
            }
        }

        log.info("Expired files cleanup job finished successfully. Total files cleaned: {}", totalDeleted);
    }
}
