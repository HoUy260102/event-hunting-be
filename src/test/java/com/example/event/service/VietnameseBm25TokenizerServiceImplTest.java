package com.example.event.service;

import com.example.event.dto.SparseVectorDTO;
import com.example.event.service.Impl.VietnameseBm25TokenizerServiceImpl;
import com.example.event.util.MurmurHash3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VietnameseBm25TokenizerServiceImplTest {

    private VietnameseBm25TokenizerServiceImpl tokenizerService;

    @BeforeEach
    void setUp() {
        tokenizerService = new VietnameseBm25TokenizerServiceImpl();
        tokenizerService.init();
    }

    @Test
    @DisplayName("So sánh điểm BM25 giữa sự kiện HCM và Hà Nội cho query tìm kiếm Hà Nội")
    void testCompareHcmAndHanoi() {
        String docHcm = "'GIỮA MỘT VẠN TOUR (MỞ RỘNG)' - PHÙNG KHÁNH LINH | CHAPTER 5: LIVE EXPERIENCE IN HCMC Liveshow & Concert BLACK SWAN LABEL Hồ Chí Minh Nhà Thi Đấu Phú Thọ";
        String docHn = "'GIỮA MỘT VẠN TOUR MỞ RỘNG' - PHÙNG KHÁNH LINH | CHAPTER 3: LIVE EXPERIENCE IN HÀ NỘI Liveshow & Concert BLACK SWAN LABEL Hà Nội TRUNG TÂM HỘI NGHỊ QUỐC GIA";

        String query = "Concert Phùng Khánh Linh ở Hà Nội";

        SparseVectorDTO vecHcm = tokenizerService.toDocumentSparseVector(docHcm, 100);
        SparseVectorDTO vecHn = tokenizerService.toDocumentSparseVector(docHn, 100);
        SparseVectorDTO vecQuery = tokenizerService.toQuerySparseVector(query);

        // Tính Dot Product giữa query và document
        float scoreHcm = calculateDotProduct(vecQuery, vecHcm);
        float scoreHn = calculateDotProduct(vecQuery, vecHn);

        System.out.println("==================================================");
        System.out.println("Query: " + query);
        System.out.println("Query Tokens: " + tokenizerService.extractTokens(query));
        System.out.println("Doc HCM BM25 Score: " + scoreHcm);
        System.out.println("Doc HN BM25 Score: " + scoreHn);
        System.out.println("==================================================");

        assertTrue(scoreHn > scoreHcm, "Điểm sự kiện Hà Nội PHẢI cao hơn HCM trong nhánh BM25");
    }

    private float calculateDotProduct(SparseVectorDTO q, SparseVectorDTO doc) {
        float sum = 0f;
        java.util.Map<Integer, Float> docMap = new java.util.HashMap<>();
        for (int i = 0; i < doc.getIndices().size(); i++) {
            docMap.put(doc.getIndices().get(i), doc.getValues().get(i));
        }
        for (int i = 0; i < q.getIndices().size(); i++) {
            int qIdx = q.getIndices().get(i);
            float qVal = q.getValues().get(i);
            if (docMap.containsKey(qIdx)) {
                sum += qVal * docMap.get(qIdx);
            }
        }
        return sum;
    }

    @Test
    @DisplayName("Tách từ 1-gram và 2-gram loại bỏ stop words đúng luật ngắt nhịp")
    void testExtractTokens_PreservesCompoundsAndFiltersStopwords() {
        String input = "Liveshow ca nhạc của Đen Vâu tại Hà Nội!";
        List<String> tokens = tokenizerService.extractTokens(input);

        assertNotNull(tokens);
        assertFalse(tokens.isEmpty());

        // Kiểm tra giữ lại từ đơn có nghĩa
        assertTrue(tokens.contains("liveshow"));
        assertTrue(tokens.contains("ca"));
        assertTrue(tokens.contains("nhạc"));
        assertTrue(tokens.contains("đen"));
        assertTrue(tokens.contains("vâu"));
        assertTrue(tokens.contains("hà"));
        assertTrue(tokens.contains("nội"));

        // Kiểm tra loại bỏ từ dừng đơn
        assertFalse(tokens.contains("của"));
        assertFalse(tokens.contains("tại"));

        // Kiểm tra ghép 2-gram chuẩn
        assertTrue(tokens.contains("ca nhạc"));
        assertTrue(tokens.contains("đen vâu"));
        assertTrue(tokens.contains("hà nội"));

        // Kiểm tra KHÔNG ghép rác xuyên qua stop words
        assertFalse(tokens.contains("nhạc của"));
        assertFalse(tokens.contains("của đen"));
        assertFalse(tokens.contains("nhạc đen"));
        assertFalse(tokens.contains("vâu tại"));
        assertFalse(tokens.contains("tại hà"));
        assertFalse(tokens.contains("vâu hà"));
    }

    @Test
    @DisplayName("Tách từ 1-gram, 2-gram và 3-gram bắt chuẩn tên nghệ sĩ và địa danh 3 từ")
    void testExtractTokens_Preserves3GramsForArtistNamesAndLocations() {
        String input = "Đêm nhạc của Hà Anh Tuấn tại Hồ Chí Minh!";
        List<String> tokens = tokenizerService.extractTokens(input);

        assertNotNull(tokens);

        // 1-gram
        assertTrue(tokens.contains("hà"));
        assertTrue(tokens.contains("anh"));
        assertTrue(tokens.contains("tuấn"));
        assertTrue(tokens.contains("hồ"));
        assertTrue(tokens.contains("chí"));
        assertTrue(tokens.contains("minh"));

        // 2-gram
        assertTrue(tokens.contains("hà anh"));
        assertTrue(tokens.contains("anh tuấn"));
        assertTrue(tokens.contains("hồ chí"));
        assertTrue(tokens.contains("chí minh"));

        // 3-gram
        assertTrue(tokens.contains("hà anh tuấn"), "Phải bắt được tên nghệ sĩ 3 từ: hà anh tuấn");
        assertTrue(tokens.contains("hồ chí minh"), "Phải bắt được địa danh 3 từ: hồ chí minh");

        // Ngắt nhịp: không được ghép xuyên stopword 'của' và 'tại'
        assertFalse(tokens.contains("nhạc của hà"));
        assertFalse(tokens.contains("của hà anh"));
        assertFalse(tokens.contains("nhạc hà anh"));
        assertFalse(tokens.contains("tuấn tại hồ"));
        assertFalse(tokens.contains("tại hồ chí"));
        assertFalse(tokens.contains("tuấn hồ chí"));
    }

    @Test
    @DisplayName("MurmurHash3 băm chuỗi tất định và trả về số nguyên không âm")
    void testMurmurHash3_DeterministicAndPositive() {
        int hash1 = MurmurHash3.hashToken("đen vâu");
        int hash2 = MurmurHash3.hashToken("đen vâu");
        int hashDifferent = MurmurHash3.hashToken("hà nội");

        assertEquals(hash1, hash2, "Cùng một chuỗi băm phải luôn cho ra cùng một index");
        assertNotEquals(hash1, hashDifferent, "Hai chuỗi khác nhau phải cho ra hash khác nhau");
        assertTrue(hash1 >= 0, "Index băm phải là số nguyên không âm");
        assertTrue(hashDifferent >= 0, "Index băm phải là số nguyên không âm");
    }

    @Test
    @DisplayName("Sinh Document Sparse Vector với BM25 TF bão hòa và indices được sắp xếp tăng dần")
    void testToDocumentSparseVector_ValidStructureAndSorted() {
        String docText = "Đêm nhạc Chillies tại Sài Gòn. Chillies mang đến những giai điệu bùng nổ của Chillies.";
        SparseVectorDTO vector = tokenizerService.toDocumentSparseVector(docText, 100);

        assertNotNull(vector);
        assertFalse(vector.isEmpty());
        assertEquals(vector.getIndices().size(), vector.getValues().size());

        // Kiểm tra indices được sắp xếp tăng dần
        List<Integer> indices = vector.getIndices();
        for (int i = 0; i < indices.size() - 1; i++) {
            assertTrue(indices.get(i) < indices.get(i + 1), "Indices phải được sắp xếp tăng dần và duy nhất");
        }

        // Kiểm tra giá trị BM25 TF bão hòa (từ lặp 3 lần như chillies điểm phải cao hơn nhưng < 2.2)
        for (Float val : vector.getValues()) {
            assertTrue(val > 0.0f, "TF score phải luôn dương");
            assertTrue(val <= 2.2f, "TF score với k1=1.2 không vượt quá 2.2");
        }
    }

    @Test
    @DisplayName("Sinh Query Sparse Vector với values = 1.0f cho mỗi token")
    void testToQuerySparseVector_UnitWeights() {
        String query = "liveshow Đen Vâu";
        SparseVectorDTO queryVector = tokenizerService.toQuerySparseVector(query);

        assertNotNull(queryVector);
        assertFalse(queryVector.isEmpty());
        assertEquals(queryVector.getIndices().size(), queryVector.getValues().size());

        // Kiểm tra tất cả values của query đều bằng 1.0f
        for (Float val : queryVector.getValues()) {
            assertEquals(1.0f, val, 0.0001f);
        }
    }

    @Test
    @DisplayName("Xử lý chuỗi rỗng và chuỗi toàn ký tự đặc biệt an toàn")
    void testEdgeCases_NullOrBlank() {
        assertTrue(tokenizerService.extractTokens(null).isEmpty());
        assertTrue(tokenizerService.extractTokens("   ").isEmpty());
        assertTrue(tokenizerService.extractTokens("!@#$%^&*()").isEmpty());

        SparseVectorDTO docEmpty = tokenizerService.toDocumentSparseVector("", 100);
        assertTrue(docEmpty.isEmpty());

        SparseVectorDTO queryEmpty = tokenizerService.toQuerySparseVector(null);
        assertTrue(queryEmpty.isEmpty());
    }

    @Test
    @DisplayName("Trích xuất token chuẩn tiếng Việt và giữ nguyên dạng gốc")
    void testExtractTokens_VietnameseAccentedNgrams() {
        String input = "Phùng Khánh Linh";
        List<String> tokens = tokenizerService.extractTokens(input);

        assertNotNull(tokens);

        // Kiểm tra giữ đủ tokens có dấu chuẩn
        assertTrue(tokens.contains("phùng"));
        assertTrue(tokens.contains("khánh"));
        assertTrue(tokens.contains("linh"));
        assertTrue(tokens.contains("phùng khánh"));
        assertTrue(tokens.contains("khánh linh"));
        assertTrue(tokens.contains("phùng khánh linh"));
    }

    @Test
    @DisplayName("Query không dấu khớp chính xác với Document có dấu qua Sparse Vector")
    void testUnaccentedQueryMatchesAccentedDocument() {
        String docText = "Đại nhạc hội 'Giữa một vạn tour' - Phùng Khánh Linh";
        SparseVectorDTO docVector = tokenizerService.toDocumentSparseVector(docText, 100);

        String unaccentedQuery = "phung khanh linh";
        SparseVectorDTO queryVector = tokenizerService.toQuerySparseVector(unaccentedQuery);

        assertNotNull(docVector);
        assertNotNull(queryVector);

        // Kiểm tra tất cả indices của query không dấu đều nằm trong Document vector
        Set<Integer> docIndices = new HashSet<>(docVector.getIndices());
        for (Integer queryIndex : queryVector.getIndices()) {
            assertTrue(docIndices.contains(queryIndex), 
                    "Index của query không dấu phải khớp với token tương ứng trong Document vector");
        }
    }

    @Test
    @DisplayName("In ra kết quả chi tiết từng bước tách từ của một đoạn văn bản mẫu")
    void testVisualInspectionSample() {
        String sample = "Liveshow 'Giữa Một Vạn Tour' - Phùng Khánh Linh cùng Hoàng Dũng tại Nhà hát Lớn Hà Nội! " +
                        "Đêm nhạc sống bùng nổ của Phùng Khánh Linh.";

        System.out.println("================================================================================");
        System.out.println("VĂN BẢN ĐẦU VÀO:");
        System.out.println(sample);
        System.out.println("================================================================================");

        List<String> rawTokens = tokenizerService.extractTokens(sample);
        System.out.println("\n1. TOKENS GỐC (extractTokens - 1-gram, 2-gram, 3-gram có dấu):");
        System.out.println("Tổng số: " + rawTokens.size());
        System.out.println(rawTokens);

        Set<String> tokenSet = new HashSet<>(rawTokens);
        Set<String> unaccentedTokens = new LinkedHashSet<>();
        for (String t : rawTokens) {
            String u = VietnameseBm25TokenizerServiceImpl.removeAccents(t);
            if (!tokenSet.contains(u)) {
                unaccentedTokens.add(u);
            }
        }

        System.out.println("\n2. TOKENS KHÔNG DẤU SINH THÊM (nhận hệ số phạt 0.85f):");
        System.out.println("Tổng số: " + unaccentedTokens.size());
        System.out.println(unaccentedTokens);

        SparseVectorDTO docVector = tokenizerService.toDocumentSparseVector(sample, 100);
        System.out.println("\n3. VECTOR DOCUMENT QDRANT (indices & values):");
        System.out.println("Số chiều vector: " + docVector.getIndices().size());
        for (int i = 0; i < Math.min(10, docVector.getIndices().size()); i++) {
            System.out.println("   Index: " + docVector.getIndices().get(i) + " -> Value (TF): " + docVector.getValues().get(i));
        }

        System.out.println("\n4. TEST TRUY VẤN KHÔNG DẤU: 'phung khanh linh'");
        SparseVectorDTO queryUnaccented = tokenizerService.toQuerySparseVector("phung khanh linh");
        System.out.println("Indices query: " + queryUnaccented.getIndices());
        System.out.println("Values query:  " + queryUnaccented.getValues());

        // Tính điểm khớp giữa query và document
        float dotProduct = 0.0f;
        for (int i = 0; i < queryUnaccented.getIndices().size(); i++) {
            int qIdx = queryUnaccented.getIndices().get(i);
            int docPos = docVector.getIndices().indexOf(qIdx);
            if (docPos >= 0) {
                dotProduct += queryUnaccented.getValues().get(i) * docVector.getValues().get(docPos);
            }
        }
        System.out.println("-> Điểm khớp BM25 thô (Dot Product): " + dotProduct);
        assertTrue(dotProduct > 0.0f, "Phải khớp và có điểm dương!");

        SparseVectorDTO qTrienLam = tokenizerService.toQuerySparseVector("triễn lãm");
        System.out.println("\n=== SPARSE VECTOR CHO 'triễn lãm' ===");
        System.out.println("Indices: " + qTrienLam.getIndices());
        System.out.println("Values:  " + qTrienLam.getValues());
        System.out.println("================================================================================");
    }
}

