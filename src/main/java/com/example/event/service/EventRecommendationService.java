package com.example.event.service;

import com.example.event.dto.EventSearchPublicDTO;
import java.util.List;

public interface EventRecommendationService {
    /**
     * Chạy định kỳ để tính toán ma trận gợi ý dựa trên tương tác trong vòng N ngày gần nhất.
     * Mặc định là 180 ngày (6 tháng).
     */
    void runRecommendationJob(int days);

    /**
     * Thuật toán cũ tính ma trận hoàn toàn trên RAM của Java (Legacy).
     * Được giữ lại làm phương án đối chứng/dự phòng.
     */
    void runRecommendationJobLegacy(int days);

    /**
     * Lấy danh sách sự kiện gợi ý đã được cá nhân hóa cho người dùng hiện tại từ Redis.
     * @param limit Số lượng sự kiện tối đa cần lấy.
     */
    List<EventSearchPublicDTO> getPersonalizedRecommendations(int limit);
}
