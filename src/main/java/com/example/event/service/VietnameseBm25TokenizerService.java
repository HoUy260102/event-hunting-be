package com.example.event.service;

import com.example.event.dto.SparseVectorDTO;

import java.util.List;

public interface VietnameseBm25TokenizerService {

    /**
     * Tách văn bản tiếng Việt thành danh sách token sạch (Tên riêng, Từ ghép từ điển, Từ đơn).
     * Áp dụng Maximum Matching và nhận diện thực thể (Proper Nouns), loại bỏ Stop Words và số trôi nổi.
     *
     * @param text Văn bản nguồn
     * @return Danh sách token đã làm sạch
     */
    List<String> extractTokens(String text);

    /**
     * Chuyển đổi văn bản sự kiện có phân tầng trọng số trường (Field Boosting):
     * Các token trong coreText (Title, Artist, Location) được nhân hệ số ưu tiên (coreBoost).
     *
     * @param coreText Văn bản nòng cốt (Tên sự kiện, ca sĩ, địa điểm, danh mục)
     * @param descText Văn bản mô tả chi tiết (nếu không có, truyền null)
     * @param avgDocLength Độ dài trung bình tham chiếu (nếu <= 0, lấy mặc định 100)
     * @return SparseVectorDTO
     */
    SparseVectorDTO toDocumentSparseVector(String coreText, String descText, int avgDocLength);

    /**
     * Chuyển đổi câu truy vấn tìm kiếm của người dùng thành Sparse Vector.
     * Gán trọng số value = 1.0f cho mỗi token để phối hợp với Qdrant modifier: "idf".
     *
     * @param query Từ khóa tìm kiếm của người dùng
     * @return SparseVectorDTO cho query
     */
    SparseVectorDTO toQuerySparseVector(String query);
}
