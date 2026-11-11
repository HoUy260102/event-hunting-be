package com.example.event.component;

import com.example.event.service.EventRecommendationService;
import com.example.event.service.QdrantClientService;
import com.example.event.service.QdrantDataMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final EventRecommendationService eventRecommendationService;

    @Value("${app.recommendation.days-window:180}")
    private int daysWindow;

    @Override
    public void run(ApplicationArguments args) {
        // Chạy ngầm trong background thread để không chặn tiến trình khởi động server
        CompletableFuture.runAsync(() -> {
            try {
                // Đợi 3 giây sau khi server khởi động hoàn tất các kết nối
                Thread.sleep(3000);

                warmupEventsSearchData();
                warmupRecommendationData();

            } catch (Exception e) {
                log.error("[QDRANT-WARMUP] Lỗi không mong muốn trong tiến trình warmup Qdrant: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Khởi tạo và warmup dữ liệu sự kiện phục vụ Search Engine.
     */
    private void warmupEventsSearchData() {
        try {
            log.info("[QDRANT-WARMUP] Đang kiểm tra trạng thái dữ liệu sự kiện trên Qdrant...");
            qdrantClientService.initCollectionIfNotExists();

            long currentCount = qdrantClientService.countPoints();
            if (currentCount > 0) {
                log.info("[QDRANT-WARMUP] Collection Qdrant đã sẵn sàng với {} sự kiện. Bỏ qua tự động reindex.", currentCount);
                return;
            }

            log.info("[QDRANT-WARMUP] Qdrant hiện tại chưa có dữ liệu (0 points). Bắt đầu tự động warmup các sự kiện PUBLISHED...");
            int totalIndexed = qdrantDataMigrationService.reindexAllPublishedEvents();
            log.info("[QDRANT-WARMUP] Hoàn tất warmup dữ liệu sự kiện lên Qdrant thành công! Tổng cộng: {} sự kiện.", totalIndexed);
        } catch (Exception e) {
            log.error("[QDRANT-WARMUP] Lỗi khi warmup dữ liệu sự kiện (Search): {}", e.getMessage(), e);
        }
    }

    /**
     * Khởi tạo và đồng bộ dữ liệu gợi ý người dùng phục vụ Collaborative Filtering.
     */
    private void warmupRecommendationData() {
        try {
            log.info("[QDRANT-WARMUP] Bắt đầu tính toán và đồng bộ dữ liệu gợi ý người dùng (users_cf) lúc khởi động...");
            eventRecommendationService.runRecommendationJob(daysWindow);
            log.info("[QDRANT-WARMUP] Hoàn tất đồng bộ dữ liệu gợi ý người dùng lên Qdrant thành công!");
        } catch (Exception e) {
            log.error("[QDRANT-WARMUP] Lỗi khi warmup dữ liệu gợi ý người dùng (Recommendation): {}", e.getMessage(), e);
        }
    }
}

