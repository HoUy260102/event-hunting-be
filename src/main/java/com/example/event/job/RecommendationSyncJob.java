package com.example.event.job;

import com.example.event.service.EventRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationSyncJob {

    private final EventRecommendationService eventRecommendationService;

    @Value("${app.recommendation.days-window:180}")
    private int daysWindow;

    /**
     * Tác vụ lập lịch chạy ngầm định kỳ hàng ngày vào lúc 3 giờ sáng.
     * Quét tương tác người dùng, tính ma trận thưa Sparse Vector và đồng bộ lên Qdrant & Redis.
     * ShedLock đảm bảo chỉ 1 node chạy trong môi trường phân tán (Cluster / Multi-instances).
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(
            name = "recommendationSyncJob",
            lockAtMostFor = "PT2H",
            lockAtLeastFor = "PT5M"
    )
    public void runDailyRecommendationJob() {
        log.info("[RECOMMENDATION-JOB] Bắt đầu chạy Job lập lịch đồng bộ gợi ý hàng ngày (cửa sổ {} ngày).", daysWindow);
        try {
            eventRecommendationService.runRecommendationJob(daysWindow);
            log.info("[RECOMMENDATION-JOB] Hoàn thành Job lập lịch đồng bộ gợi ý thành công.");
        } catch (Exception e) {
            log.error("[RECOMMENDATION-JOB] Lỗi trong quá trình chạy Job đồng bộ gợi ý: {}", e.getMessage(), e);
        }
    }
}

