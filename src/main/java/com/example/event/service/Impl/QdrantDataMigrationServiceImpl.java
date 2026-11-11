package com.example.event.service.Impl;

import com.example.event.constant.EventStatus;
import com.example.event.entity.Event;
import com.example.event.repository.EventRepository;
import com.example.event.service.QdrantClientService;
import com.example.event.service.QdrantDataMigrationService;
import com.example.event.specification.EventSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class QdrantDataMigrationServiceImpl implements QdrantDataMigrationService {

    private final EventRepository eventRepository;
    private final QdrantClientService qdrantClientService;

    private static final int BATCH_SIZE = 20;

    @Override
    public int reindexAllPublishedEvents() {
        log.info("[QDRANT-MIGRATION] Bắt đầu quá trình reindex toàn bộ sự kiện PUBLISHED sang Qdrant...");

        // Khởi tạo collection nếu chưa có
        qdrantClientService.initCollectionIfNotExists();

        Specification<Event> spec = Specification.where(EventSpecification.isNotDeleted())
                .and(EventSpecification.hasStatus(EventStatus.PUBLISHED))
                .and(EventSpecification.fetchDetails());

        int pageIndex = 0;
        int totalIndexed = 0;

        while (true) {
            Pageable pageable = PageRequest.of(pageIndex, BATCH_SIZE, Sort.by("id").ascending());
            Page<Event> eventPage = eventRepository.findAll(spec, pageable);

            if (eventPage.isEmpty()) {
                break;
            }

            for (Event event : eventPage.getContent()) {
                try {
                    qdrantClientService.upsertEvent(event);
                    totalIndexed++;
                    // Giữ nhịp 100ms giữa các sự kiện để bảo vệ hạn mức Gemini Embedding API
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[QDRANT-MIGRATION] Tiến trình reindex bị gián đoạn.");
                    return totalIndexed;
                } catch (Exception e) {
                    log.error("[QDRANT-MIGRATION] Lỗi index eventId={}: {}", event.getId(), e.getMessage());
                }
            }

            log.info("[QDRANT-MIGRATION] Đã hoàn thành trang {}/{} (Tổng đã index: {})",
                    pageIndex + 1, eventPage.getTotalPages(), totalIndexed);

            if (!eventPage.hasNext()) {
                break;
            }
            pageIndex++;
        }

        log.info("[QDRANT-MIGRATION] Quá trình reindex hoàn tất. Tổng cộng: {} sự kiện.", totalIndexed);
        return totalIndexed;
    }
}

