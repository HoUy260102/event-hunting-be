package com.example.event.service;

import com.example.event.dto.EventSearchPublicDTO;
import java.util.List;

public interface EventRecommendationService {
    /**
     * Chạy định kỳ để tính toán ma trận gợi ý dựa trên tương tác trong vòng N ngày
     * gần nhất.
     * Mặc định là 180 ngày (6 tháng).
     */
    void runRecommendationJob(int days);

    /**
     * Thuật toán cũ tính ma trận hoàn toàn trên RAM của Java (Legacy).
     * Được giữ lại làm phương án đối chứng/dự phòng.
     */
    void runRecommendationJobLegacy(int days);

    /**
     * Lấy danh sách sự kiện gợi ý cá nhân hóa có hỗ trợ phân trang (skip / limit).
     * 
     * @param page Số thứ tự trang (1-indexed: 1, 2, 3...).
     * @param size Số lượng sự kiện cần lấy cho mỗi trang.
     */
    List<EventSearchPublicDTO> getPersonalizedRecommendations(int page, int size);
}
