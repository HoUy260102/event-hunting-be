package com.example.event.service;

import com.example.event.dto.SparseVectorDTO;

import java.util.List;

public interface VietnameseBm25TokenizerService {

    /**
     * Tách văn bản tiếng Việt thành danh sách token 1-gram, 2-gram và 3-gram sau khi chuẩn hóa và loại bỏ Stop Words.
     * Áp dụng nguyên tắc ngắt nhịp (không ghép n-gram xuyên qua Stop Words).
     * Bắt chính xác tên nghệ sĩ và địa danh 3 từ (ví dụ: "Hà Anh Tuấn", "Hồ Chí Minh").
     *
     * @param text Văn bản nguồn
     * @return Danh sách token đã làm sạch
     */
    List<String> extractTokens(String text);

    /**
     * Chuyển đổi văn bản sự kiện thành Sparse Vector theo chuẩn Okapi BM25 Term Frequency Saturation:
     * value = f * (k1 + 1) / (f + k1 * (1 - b + b * (docLen / avgDocLen)))
     *
     * @param text Văn bản tổng hợp của sự kiện (tên, ca sĩ, địa điểm, mô tả)
     * @param avgDocLength Độ dài trung bình tham chiếu (nếu <= 0, lấy mặc định 100)
     * @return SparseVectorDTO chứa danh sách indices và values
     */
    SparseVectorDTO toDocumentSparseVector(String text, int avgDocLength);

    /**
     * Chuyển đổi câu truy vấn tìm kiếm của người dùng thành Sparse Vector.
     * Gán trọng số value = 1.0f cho mỗi token để phối hợp với Qdrant modifier: "idf".
     *
     * @param query Từ khóa tìm kiếm của người dùng
     * @return SparseVectorDTO cho query
     */
    SparseVectorDTO toQuerySparseVector(String query);
}
