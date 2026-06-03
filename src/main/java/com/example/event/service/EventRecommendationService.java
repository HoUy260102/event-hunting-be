package com.example.event.service;

import com.example.event.dto.EventSearchPublicDTO;
import java.util.List;

public interface EventRecommendationService {
    /**
     * Chạy định kỳ để tính toán ma trận gợi ý dựa trên tương tác trong vòng N ngày gần nhất.
     * Mặc định là 90 ngày.
     */
    void runRecommendationJob(int days);

    /**
     * Lấy danh sách sự kiện gợi ý đã được cá nhân hóa cho người dùng hiện tại từ Redis.
     * @param limit Số lượng sự kiện tối đa cần lấy.
     */
    List<EventSearchPublicDTO> getPersonalizedRecommendations(int limit);
}
