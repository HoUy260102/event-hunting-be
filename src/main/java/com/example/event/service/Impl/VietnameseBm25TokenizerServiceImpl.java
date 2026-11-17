package com.example.event.service.Impl;

import com.example.event.dto.SparseVectorDTO;
import com.example.event.service.VietnameseBm25TokenizerService;
import com.example.event.util.MurmurHash3;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class VietnameseBm25TokenizerServiceImpl implements VietnameseBm25TokenizerService {

    private static final String STOPWORDS_FILE = "stopwords/vietnamese_stopwords.txt";
    private static final float K1 = 1.2f;
    private static final float B = 0.75f;
    private static final int DEFAULT_AVG_DOC_LENGTH = 100;
    private static final float UNACCENTED_PENALTY = 0.85f;
    private static final Pattern DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private final Set<String> stopWords = new HashSet<>();

    @PostConstruct
    public void init() {
        loadStopWords();
    }

    private void loadStopWords() {
        try {
            ClassPathResource resource = new ClassPathResource(STOPWORDS_FILE);
            if (!resource.exists()) {
                log.warn("[BM25] Không tìm thấy file từ dừng '{}'. Bỏ qua nạp stop words.", STOPWORDS_FILE);
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim().toLowerCase();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        stopWords.add(trimmed);
                    }
                }
            }
            log.info("[BM25] Đã nạp thành công {} từ dừng tiếng Việt từ file '{}'.",
                    stopWords.size(), STOPWORDS_FILE);
        } catch (Exception e) {
            log.error("[BM25] Lỗi đọc file stopwords: {}", e.getMessage(), e);
        }
    }

    /**
     * Chuyển đổi văn bản tiếng Việt có dấu thành không dấu (loại bỏ dấu thanh và
     * chuẩn hóa đ/Đ -> d/D).
     *
     * @param text Chuỗi văn bản tiếng Việt
     * @return Chuỗi không dấu tương ứng
     */
    public static String removeAccents(String text) {
        if (text == null) {
            return null;
        }
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        return DIACRITICAL_MARKS.matcher(nfd)
                .replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D');
    }

    @Override
    public List<String> extractTokens(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        // 1. Chuẩn hóa: lowercase, thay ký tự đặc biệt bằng khoảng trắng
        String cleanText = text.toLowerCase()
                .replaceAll("[^\\p{L}\\p{Nd}\\s]", " ")
                .trim();

        if (cleanText.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Tách danh sách tokens chuẩn (1-gram, 2-gram, 3-gram)
        return tokenizeNgrams(cleanText);
    }

    private List<String> tokenizeNgrams(String cleanText) {
        String[] words = cleanText.split("\\s+");
        List<String> tokens = new ArrayList<>();

        for (int i = 0; i < words.length; i++) {
            String w1 = words[i];

            // 1-gram: Bỏ qua nếu là stop word hoặc độ dài <= 1 ký tự
            if (!stopWords.contains(w1) && w1.length() > 1) {
                tokens.add(w1);
            }

            // 2-gram: Ghép 2 từ liền kề nếu cả 2 từ đều KHÔNG phải là stop word (nguyên tắc
            // ngắt nhịp)
            if (i < words.length - 1) {
                String w2 = words[i + 1];
                if (!stopWords.contains(w1) && !stopWords.contains(w2) && w1.length() > 1 && w2.length() > 1) {
                    String biGram = w1 + " " + w2;
                    if (!stopWords.contains(biGram)) {
                        tokens.add(biGram);
                    }
                }
            }

            // 3-gram: Ghép 3 từ liền kề nếu cả 3 từ đều KHÔNG phải là stop word (bắt tên
            // nghệ sĩ/địa danh: "Hà Anh Tuấn", "Hồ Chí Minh")
            if (i < words.length - 2) {
                String w2 = words[i + 1];
                String w3 = words[i + 2];
                if (!stopWords.contains(w1) && !stopWords.contains(w2) && !stopWords.contains(w3)
                        && w1.length() > 1 && w2.length() > 1 && w3.length() > 1) {
                    String triGram = w1 + " " + w2 + " " + w3;
                    if (!stopWords.contains(triGram)) {
                        tokens.add(triGram);
                    }
                }
            }
        }

        return tokens;
    }

    @Override
    public SparseVectorDTO toDocumentSparseVector(String text, int avgDocLength) {
        List<String> tokens = extractTokens(text);
        if (tokens.isEmpty()) {
            return SparseVectorDTO.empty();
        }

        // Tạo Set tra cứu O(1) để kiểm tra tồn tại siêu tốc mà không làm mất List đếm
        // tần suất
        Set<String> tokenSet = new HashSet<>(tokens);

        // Tạo tập hợp các token không dấu sinh thêm (chỉ lấy những từ chưa nằm trong
        // tokens gốc)
        Set<String> unaccentedTokens = new HashSet<>();
        for (String t : tokens) {
            String u = removeAccents(t);
            if (!tokenSet.contains(u)) {
                unaccentedTokens.add(u);
            }
        }

        // Gộp tokens gốc và tokens không dấu sinh thêm để tính toán trọng số BM25
        List<String> allTokens = new ArrayList<>(tokens);
        allTokens.addAll(unaccentedTokens);

        Map<String, Integer> termFreqs = new LinkedHashMap<>();
        for (String token : allTokens) {
            termFreqs.merge(token, 1, Integer::sum);
        }

        int docLength = allTokens.size();
        int avgLen = avgDocLength > 0 ? avgDocLength : DEFAULT_AVG_DOC_LENGTH;

        // Dùng TreeMap để sắp xếp indices theo thứ tự tăng dần (chuẩn Qdrant)
        Map<Integer, Float> indexToValue = new TreeMap<>();

        for (Map.Entry<String, Integer> entry : termFreqs.entrySet()) {
            String token = entry.getKey();
            int freq = entry.getValue();

            int index = MurmurHash3.hashToken(token);

            // Công thức Okapi BM25 TF Saturation
            float lengthNorm = 1.0f - B + B * ((float) docLength / avgLen);
            float tfScore = (freq * (K1 + 1.0f)) / (freq + K1 * lengthNorm);

            // Áp dụng hệ số phạt đối với token không dấu sinh thêm (giảm 15% điểm so với từ
            // gốc có dấu)
            if (unaccentedTokens.contains(token)) {
                tfScore *= UNACCENTED_PENALTY;
            }

            // Nếu xảy ra va chạm hash, giữ giá trị lớn nhất
            indexToValue.merge(index, tfScore, Math::max);
        }

        List<Integer> indices = new ArrayList<>(indexToValue.keySet());
        List<Float> values = new ArrayList<>(indexToValue.values());

        return SparseVectorDTO.builder()
                .indices(indices)
                .values(values)
                .build();
    }

    @Override
    public SparseVectorDTO toQuerySparseVector(String query) {
        List<String> tokens = extractTokens(query);

        // Fallback: nếu toàn bộ query là stop words hoặc từ 1 ký tự, thử giữ lại các từ
        // gốc
        if (tokens.isEmpty() && query != null && !query.isBlank()) {
            String[] rawWords = query.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}\\s]", " ").trim().split("\\s+");
            tokens = Arrays.stream(rawWords)
                    .filter(w -> !w.isBlank())
                    .toList();
        }

        if (tokens.isEmpty()) {
            return SparseVectorDTO.empty();
        }

        // Với Query: bao gồm cả token gốc và token không dấu để hỗ trợ tìm kiếm 2 chiều
        Set<String> allQueryTokens = new LinkedHashSet<>(tokens);
        for (String t : tokens) {
            allQueryTokens.add(removeAccents(t));
        }

        // Đối với query: Gán weight = 1.0 cho mỗi token duy nhất (phù hợp với Qdrant
        // modifier: "idf")
        Map<Integer, Float> indexToValue = new TreeMap<>();
        for (String token : allQueryTokens) {
            int index = MurmurHash3.hashToken(token);
            indexToValue.put(index, 1.0f);
        }

        List<Integer> indices = new ArrayList<>(indexToValue.keySet());
        List<Float> values = new ArrayList<>(indexToValue.values());

        return SparseVectorDTO.builder()
                .indices(indices)
                .values(values)
                .build();
    }
}
