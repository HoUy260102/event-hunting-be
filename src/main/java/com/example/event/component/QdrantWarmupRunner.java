package com.example.event.component;

import com.example.event.service.QdrantClientService;
import com.example.event.service.QdrantDataMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class QdrantWarmupRunner implements ApplicationRunner {

    private final QdrantClientService qdrantClientService;
    private final QdrantDataMigrationService qdrantDataMigrationService;

    @Override
    public void run(ApplicationArguments args) {
        // Chạy ngầm trong background thread để không chặn tiến trình khởi động server
        CompletableFuture.runAsync(() -> {
            try {
                // Đợi 3 giây sau khi server khởi động hoàn tất các kết nối
                Thread.sleep(3000);

                log.info("[QDRANT-WARMUP] Đang kiểm tra trạng thái dữ liệu trên Qdrant...");
                qdrantClientService.initCollectionIfNotExists();

                long currentCount = qdrantClientService.countPoints();
                if (currentCount > 0) {
                    log.info("[QDRANT-WARMUP] Collection Qdrant đã sẵn sàng với {} sự kiện. Bỏ qua tự động warmup.", currentCount);
                    return;
                }

                log.info("[QDRANT-WARMUP] Qdrant hiện tại chưa có dữ liệu (0 points). Bắt đầu tự động warmup các sự kiện PUBLISHED theo từng Batch...");
                int totalIndexed = qdrantDataMigrationService.reindexAllPublishedEvents();
                log.info("[QDRANT-WARMUP] Hoàn tất warmup dữ liệu lên Qdrant thành công! Tổng cộng: {} sự kiện.", totalIndexed);

            } catch (Exception e) {
                log.error("[QDRANT-WARMUP] Lỗi trong tiến trình warmup dữ liệu Qdrant: {}", e.getMessage(), e);
            }
        });
    }
}

