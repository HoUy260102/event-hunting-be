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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class VietnameseBm25TokenizerServiceImpl implements VietnameseBm25TokenizerService {

    private static final String STOPWORDS_FILE = "stopwords/vietnamese_stopwords.txt";
    private static final String DICTIONARY_FILE = "dictionary/vietnamese_words.txt";

    // Tham số cấu hình thuật toán Okapi BM25
    private static final float K1 = 1.2f;
    private static final float B = 0.75f;
    private static final int DEFAULT_AVG_DOC_LENGTH = 100;

    // Hệ số phạt và ưu tiên điểm số
    private static final float UNACCENTED_PENALTY = 0.75f; // Giảm 25% điểm cho từ không dấu
    private static final float COMPACT_PENALTY = 0.60f;   // Giảm 40% điểm cho từ viết liền (vd: haanhtuan)
    private static final float CORE_FIELD_BOOST = 2.5f;   // Nhân 2.5x điểm cho Title & Artist so với Description

    private static final Pattern DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    // Giữ lại dấu gạch ngang của nghệ danh viết tắt ngắn (1 trong 2 vế <= 2 ký tự): M-TP -> M_TP, G-Dragon -> G_Dragon, B-Ray -> B_Ray
    private static final Pattern HYPHEN_ARTIST_PATTERN = Pattern.compile(
            "(?U)(?<!\\p{L})(\\p{L}{1,2})-(\\p{L}+)(?!\\p{L})|(?U)(?<!\\p{L})(\\p{L}+)-(\\p{L}{1,2})(?!\\p{L})");

    // Ranh giới phân tách các Segment độc lập (dấu gạch ngang, gạch đứng, phẩy, hai chấm, chấm phẩy, gạch chéo, ngoặc...)
    private static final Pattern SEGMENT_DELIMITER_PATTERN = Pattern.compile("\\s*[-–—|,:;/~()\\[\\]{}'\"]+\\s*");

    // Chuẩn hóa giá tiền: 500 k -> 500k, 1 tr -> 1tr
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?i)(\\d+)\\s*(k|tr|triệu|vnd|đ)\\b");

    // Chuẩn hóa ngày tháng: 30/4 -> 30_4, 14/2 -> 14_2
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})[/\\-](\\d{1,2})\\b");

    // Chuẩn hóa thứ tự/mùa: mùa 2 -> mua_2, số 5 -> so_5, vol 1 -> vol_1
    private static final Pattern SERIES_PATTERN = Pattern.compile("(?i)\\b(mùa|tập|số|vol|season)\\s+(\\d+)\\b");

    // Nhận diện số 1 chữ số đứng đơn lẻ (0-9) để lọc bỏ số trôi nổi (tầng 3, cổng 1)
    private static final Pattern SINGLE_DIGIT_PATTERN = Pattern.compile("^\\d$");

    private final Set<String> stopWords = new HashSet<>();
    private final Set<String> compoundDictionary = new HashSet<>();
    private int maxCompoundWordCount = 4;

    @PostConstruct
    public void init() {
        loadStopWords();
        loadDictionary();
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

    private void loadDictionary() {
        try {
            ClassPathResource resource = new ClassPathResource(DICTIONARY_FILE);
            if (!resource.exists()) {
                log.warn("[BM25] Không tìm thấy file từ điển '{}'.", DICTIONARY_FILE);
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int maxWords = 1;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim().toLowerCase();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        String normalized = trimmed.replace("-", " ").replaceAll("\\s+", " ");
                        compoundDictionary.add(normalized);
                        int wordCount = normalized.split("\\s+").length;
                        if (wordCount > maxWords) {
                            maxWords = wordCount;
                        }
                    }
                }
                this.maxCompoundWordCount = Math.min(maxWords, 4);
            }
            log.info("[BM25] Đã nạp thành công {} từ ghép tiếng Việt từ file '{}' (RAM ước tính: ~{} MB).",
                    compoundDictionary.size(), DICTIONARY_FILE,
                    String.format("%.2f", (compoundDictionary.size() * 64.0) / (1024 * 1024)));
        } catch (Exception e) {
            log.error("[BM25] Lỗi đọc file từ điển: {}", e.getMessage(), e);
        }
    }

    /**
     * Chuyển đổi văn bản tiếng Việt có dấu thành không dấu (loại bỏ dấu thanh và chuẩn hóa đ/Đ -> d/D).
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

    /**
     * Tiền xử lý ký tự đặc biệt: nghệ danh có gạch nối, giá tiền, ngày tháng, thứ tự.
     */
    private String normalizeSpecialTokens(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String result = HYPHEN_ARTIST_PATTERN.matcher(text).replaceAll(mr -> {
            String g1 = mr.group(1);
            String g2 = mr.group(2);
            if (g1 != null && g2 != null) {
                return g1 + "_" + g2;
            }
            return mr.group(3) + "_" + mr.group(4);
        });
        result = PRICE_PATTERN.matcher(result).replaceAll("$1$2");
        result = DATE_PATTERN.matcher(result).replaceAll("$1_$2");
        result = SERIES_PATTERN.matcher(result).replaceAll("$1_$2");
        return result;
    }

    // =========================================================================
    // LÕI TÁCH TỪ (EXTRACT TOKENS) - PHƯƠNG PHÁP SEGMENT & SINGLE PASS
    // =========================================================================

    @Override
    public List<String> extractTokens(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();

        // Bước 1: Chuẩn hóa ký tự đặc biệt (M-TP -> m_tp, 30/4 -> 30_4, 500k)
        String preprocessed = normalizeSpecialTokens(text);

        // Bước 2: Tách thành các Segment độc lập theo dấu câu/ký tự ngắt
        String[] segments = SEGMENT_DELIMITER_PATTERN.split(preprocessed);

        // Bước 3: Duyệt từng segment độc lập theo phương pháp Single Pass (Duyệt 1 lượt con trỏ nhảy cóc)
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            tokenizeSegment(segment, tokens);
        }

        return tokens;
    }

    /**
     * Tách từ cho một phân đoạn (Segment) theo phương pháp Single Pass (Duyệt 1 lượt con trỏ):
     * 1. Xử lý từ đã tiền xử lý gạch dưới (m_tp, 30_4)
     * 2. Ưu tiên 1: Từ ghép trong từ điển Maximum Matching (từ 4 từ -> 2 từ)
     * 3. Ưu tiên 2: Cụm từ viết hoa liên tiếp (Tên riêng, Nghệ danh, Địa danh tự do không giới hạn số từ)
     * 4. Ưu tiên 3: Từ đơn 1-gram
     */
    private void tokenizeSegment(String segment, List<String> tokens) {
        String clean = segment.replaceAll("[^\\p{L}\\p{Nd}_\\s]", " ").trim();
        if (clean.isEmpty()) {
            return;
        }
        String[] words = clean.split("\\s+");

        int i = 0;
        while (i < words.length) {
            String word = words[i];
            String lower = word.toLowerCase();

            // 1. Nếu từ đã có gạch dưới sẵn (do tiền xử lý M_TP, 30_4)
            if (lower.contains("_")) {
                addPreprocessedToken(lower, tokens);
                i++;
                continue;
            }

            // 2. ƯU TIÊN 1: Thử ghép từ trong Từ điển tiếng Việt (Maximum Matching 4 -> 2 từ)
            int matchedDictWords = tryMatchCompound(words, i, tokens);
            if (matchedDictWords > 0) {
                i += matchedDictWords;
                continue;
            }

            // 3. ƯU TIÊN 2: Quét chuỗi từ viết hoa liên tiếp (Tên riêng, Nghệ danh không giới hạn số từ)
            int matchedProperWords = tryMatchProperNoun(words, i, tokens);
            if (matchedProperWords > 0) {
                i += matchedProperWords;
                continue;
            }

            // 4. ƯU TIÊN 3: Từ đơn thông thường (1-gram)
            addSingleWord(lower, tokens);
            i++;
        }
    }

    /**
     * Thêm từ đã có gạch dưới do tiền xử lý (vd: m_tp, 30_4).
     */
    private void addPreprocessedToken(String word, List<String> tokens) {
        tokens.add(word); // "m_tp", "30_4"

        // Tách các từ con thành phần có nghĩa (vd: m_tp -> giữ lại 'tp')
        for (String sub : word.split("_")) {
            addSingleWord(sub, tokens);
        }
    }

    /**
     * Dò từ ghép trong từ điển từ độ dài lớn nhất giảm dần xuống 2 từ.
     * Lưu dạng gạch dưới (_) chuẩn NLP (vd: đêm_nhạc, hà_nội, bà_rịa_vũng_tàu).
     */
    private int tryMatchCompound(String[] words, int startIndex, List<String> tokens) {
        int maxK = Math.min(maxCompoundWordCount, words.length - startIndex);
        for (int k = maxK; k >= 2; k--) {
            String[] lowerSlice = new String[k];
            for (int j = 0; j < k; j++) {
                lowerSlice[j] = words[startIndex + j].toLowerCase();
            }
            String candidate = String.join(" ", lowerSlice);

            if (compoundDictionary.contains(candidate) && !stopWords.contains(candidate)) {
                // Thêm cụm từ ghép đầy đủ dạng gạch dưới
                tokens.add(candidate.replace(" ", "_"));

                // 2. Sinh các 2-grams con nếu k >= 3 (vd: hà_anh, anh_tuấn; bà_rịa, vũng_tàu)
                if (k >= 3) {
                    for (int j = 0; j < k - 1; j++) {
                        String biSpace = lowerSlice[j] + " " + lowerSlice[j + 1];
                        if (!stopWords.contains(biSpace)) {
                            tokens.add(lowerSlice[j] + "_" + lowerSlice[j + 1]);
                        }
                    }
                }

                // 3. Sinh các 3-grams con nếu k >= 4 (vd: bà_rịa_vũng, rịa_vũng_tàu)
                if (k >= 4) {
                    for (int j = 0; j < k - 2; j++) {
                        tokens.add(lowerSlice[j] + "_" + lowerSlice[j + 1] + "_" + lowerSlice[j + 2]);
                    }
                }

                // Thêm các từ đơn thành phần (vd: hà, nội)
                for (String sub : lowerSlice) {
                    addSingleWord(sub, tokens);
                }
                return k; // Ghép thành công k từ
            }
        }
        return 0;
    }

    /**
     * Nhận diện chuỗi từ viết hoa liên tiếp (Tên riêng, Nghệ danh, Địa danh tự do).
     * Không giới hạn số lượng từ, tự động sinh n-grams (full, 3-gram, 2-gram, 1-gram).
     */
    private int tryMatchProperNoun(String[] words, int startIndex, List<String> tokens) {
        if (!isCapitalized(words[startIndex])) {
            return 0;
        }

        // Đếm số từ viết hoa liên tiếp
        int m = 0;
        while (startIndex + m < words.length && isCapitalized(words[startIndex + m])) {
            String lower = words[startIndex + m].toLowerCase();
            if (stopWords.contains(lower)) {
                break;
            }
            m++;
        }

        if (m < 2) {
            return 0; // Cần tối thiểu 2 từ viết hoa liên tiếp
        }

        String[] lowerWords = new String[m];
        for (int j = 0; j < m; j++) {
            lowerWords[j] = words[startIndex + j].toLowerCase();
        }

        if (isAllStopWords(lowerWords)) {
            return 0;
        }

        // 1. Thêm Cụm Full-gram (vd: đen_vâu, hà_anh_tuấn, nguyễn_thị_minh_khai, nguyễn_hoàng_gia_bảo_long)
        tokens.add(String.join("_", lowerWords));

        // 2. Thêm các 2-grams con nếu m >= 3 (vd: hà_anh, anh_tuấn; nguyễn_thị, thị_minh, minh_khai)
        if (m >= 3) {
            for (int j = 0; j < m - 1; j++) {
                String biSpace = lowerWords[j] + " " + lowerWords[j + 1];
                if (!stopWords.contains(biSpace)) {
                    tokens.add(lowerWords[j] + "_" + lowerWords[j + 1]);
                }
            }
        }

        // 3. Thêm các 3-grams con nếu m >= 4 (vd: nguyễn_thị_minh, thị_minh_khai)
        if (m >= 4) {
            for (int j = 0; j < m - 2; j++) {
                tokens.add(lowerWords[j] + "_" + lowerWords[j + 1] + "_" + lowerWords[j + 2]);
            }
        }

        // 4. Thêm các từ đơn 1-gram
        for (String sub : lowerWords) {
            addSingleWord(sub, tokens);
        }

        return m; // Tiêu thụ m từ
    }

    private boolean isCapitalized(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        int codePoint = word.codePointAt(0);
        return Character.isUpperCase(codePoint);
    }

    private boolean isAllStopWords(String[] words) {
        for (String w : words) {
            if (!stopWords.contains(w)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Thêm từ đơn nếu hợp lệ (không phải stopword, độ dài > 1, không phải số trôi nổi 0-9).
     */
    private void addSingleWord(String word, List<String> tokens) {
        if (isValidSingleWord(word)) {
            tokens.add(word);
        }
    }

    /**
     * Kiểm tra tính hợp lệ của một từ đơn.
     */
    private boolean isValidSingleWord(String word) {
        return word != null
                && word.length() > 1
                && !stopWords.contains(word)
                && !SINGLE_DIGIT_PATTERN.matcher(word).matches();
    }

    // =========================================================================
    // SINH SPARSE VECTOR CHO DOCUMENT & QUERY (BM25 + MURMURHASH3)
    // =========================================================================

    @Override
    public SparseVectorDTO toDocumentSparseVector(String coreText, String descText, int avgDocLength) {
        List<String> coreTokens = extractTokens(coreText);
        List<String> descTokens = (descText != null && !descText.isBlank()) ? extractTokens(descText) : Collections.emptyList();

        if (coreTokens.isEmpty() && descTokens.isEmpty()) {
            return SparseVectorDTO.empty();
        }

        // 1. Tính tần suất có phân tầng trọng số trường (Field Boosting chuẩn BM25F)
        Map<String, Float> baseTermWeights = new LinkedHashMap<>();
        for (String t : coreTokens) {
            baseTermWeights.merge(t, CORE_FIELD_BOOST, Float::sum);
        }
        for (String t : descTokens) {
            baseTermWeights.merge(t, 1.0f, Float::sum);
        }

        // 2. Độ dài văn bản thực tế: chỉ tính theo số lượng từ gốc có dấu
        int docLength = coreTokens.size() + descTokens.size();
        int avgLen = avgDocLength > 0 ? avgDocLength : DEFAULT_AVG_DOC_LENGTH;
        float lengthNorm = 1.0f - B + B * ((float) docLength / avgLen);

        // 3. Chuẩn bị tập token không dấu và token viết liền không dấu
        Map<String, Float> unaccentedTerms = new LinkedHashMap<>();
        Map<String, Float> compactTerms = new LinkedHashMap<>();

        Set<String> baseTokenSet = baseTermWeights.keySet();
        for (Map.Entry<String, Float> entry : baseTermWeights.entrySet()) {
            String token = entry.getKey();
            float weight = entry.getValue();

            String unaccented = removeAccents(token);
            if (!baseTokenSet.contains(unaccented)) {
                unaccentedTerms.merge(unaccented, weight, Float::sum);
            }

            // Với cụm từ ghép (có _), sinh thêm token viết liền không dấu (vd: haanhtuan, dalat, mtp)
            if (token.contains("_")) {
                String compact = unaccented.replace("_", "");
                if (compact.length() >= 3 && !baseTokenSet.contains(compact)) {
                    compactTerms.merge(compact, weight, Float::sum);
                }
            }
        }

        // 4. Tính toán điểm Okapi BM25 Term Frequency Saturation
        Map<Integer, Float> indexToValue = new TreeMap<>();

        // 4.1. Token có dấu chuẩn (100% TF)
        for (Map.Entry<String, Float> entry : baseTermWeights.entrySet()) {
            float freq = entry.getValue();
            float tfScore = (freq * (K1 + 1.0f)) / (freq + K1 * lengthNorm);
            int index = MurmurHash3.hashToken(entry.getKey());
            indexToValue.merge(index, tfScore, Math::max);
        }

        // 4.2. Token không dấu tương ứng (75% TF)
        for (Map.Entry<String, Float> entry : unaccentedTerms.entrySet()) {
            float freq = entry.getValue();
            float tfScore = ((freq * (K1 + 1.0f)) / (freq + K1 * lengthNorm)) * UNACCENTED_PENALTY;
            int index = MurmurHash3.hashToken(entry.getKey());
            indexToValue.merge(index, tfScore, Math::max);
        }

        // 4.3. Token viết liền không dấu (60% TF)
        for (Map.Entry<String, Float> entry : compactTerms.entrySet()) {
            float freq = entry.getValue();
            float tfScore = ((freq * (K1 + 1.0f)) / (freq + K1 * lengthNorm)) * COMPACT_PENALTY;
            int index = MurmurHash3.hashToken(entry.getKey());
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
        if (query == null || query.isBlank()) {
            return SparseVectorDTO.empty();
        }

        List<String> rawTokens = extractTokens(query);
        if (rawTokens.isEmpty()) {
            return SparseVectorDTO.empty();
        }

        // Bổ sung phiên bản không dấu và viết liền không dấu cho query
        Set<String> expandedQueryTokens = new LinkedHashSet<>(rawTokens);
        for (String t : rawTokens) {
            String u = removeAccents(t);
            expandedQueryTokens.add(u);
            if (t.contains("_")) {
                String compact = u.replace("_", "");
                if (compact.length() >= 3) {
                    expandedQueryTokens.add(compact);
                }
            }
        }

        // Bổ sung các cụm n-grams (2-gram, 3-gram) liền kề cho truy vấn người dùng gõ thường không viết hoa (vd: "den vau", "ha anh tuan")
        String[] queryWords = query.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}_\\s]", " ").trim().split("\\s+");
        List<String> validWords = new ArrayList<>();
        for (String w : queryWords) {
            if (isValidSingleWord(w)) {
                validWords.add(w);
            }
        }
        for (int j = 0; j < validWords.size() - 1; j++) {
            String bi = validWords.get(j) + "_" + validWords.get(j + 1);
            expandedQueryTokens.add(bi);
            expandedQueryTokens.add(removeAccents(bi));
            if (j < validWords.size() - 2) {
                String tri = validWords.get(j) + "_" + validWords.get(j + 1) + "_" + validWords.get(j + 2);
                expandedQueryTokens.add(tri);
                expandedQueryTokens.add(removeAccents(tri));
            }
        }

        // Gán trọng số weight = 1.0 cho mỗi token duy nhất (chuẩn Qdrant modifier: "idf")
        Map<Integer, Float> indexToValue = new TreeMap<>();
        for (String token : expandedQueryTokens) {
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
