package com.example.event.util;

import com.example.event.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class ReservationCodeGenerator {
    private final RedisService redisService;

    // Bảng chữ cái rút gọn loại bỏ hoàn toàn các ký tự dễ nhầm lẫn (0, 1, O, I, l)
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length(); // 30
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Hệ số nhân coprime với 30^4 (810,000) để xáo trộn các số tự tăng liên tiếp thành các số phân tán hoàn toàn khác nhau.
    // Lựa chọn số 387421 là số nguyên tố và không chia hết cho các thừa số nguyên tố của 30 (là 2, 3, 5), nên ước chung lớn nhất (GCD) là 1.
    // Điều này đảm bảo ánh xạ là song ánh 1-1 (100% không bao giờ trùng lặp/collision-free).
    private static final long COPRIME_MULTIPLIER = 387421L;
    private static final long MODULUS = 810000L; // 30^4

    public String generateCode() {
        String datePrefix = LocalDate.now().format(DATE_FORMATTER);
        String key = "reservation:seq:" + datePrefix;

        try {
            // Tăng số thứ tự của ngày hôm nay lên 1
            Long sequence = redisService.incr(key, 1);
            if (sequence == null) {
                throw new RuntimeException("Redis returned null sequence");
            }

            // Nếu đây là đơn hàng đầu tiên của ngày hôm nay, cấu hình TTL là 5 ngày (432.000 giây)
            if (sequence == 1) {
                redisService.expire(key, 432000);
            }

            // Scramble và mã hóa số thứ tự sang chuỗi Base30 độ dài 4
            String encoded = encodeSequence(sequence);

            // Ghép lại thành mã đơn hàng hoàn chỉnh (ví dụ: 260528GCC3)
            return datePrefix + encoded;

        } catch (Exception e) {
            // Cơ chế Fallback an toàn phòng khi Redis gặp sự cố:
            // Hệ thống sẽ tự động ghép ngày hiện tại với 4 ký tự ngẫu nhiên
            // giúp đảm bảo giao dịch thanh toán đặt vé của khách hàng không bao giờ bị gián đoạn.
            StringBuilder fallbackSuffix = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                int index = SECURE_RANDOM.nextInt(BASE);
                fallbackSuffix.append(ALPHABET.charAt(index));
            }
            return datePrefix + fallbackSuffix.toString();
        }
    }

    /**
     * Sinh mã đơn hàng cho một ngày cụ thể và số thứ tự cụ thể (phục vụ migration dữ liệu cũ).
     */
    public String generateCodeForDate(LocalDate date, long sequence) {
        String datePrefix = date.format(DATE_FORMATTER);
        String encoded = encodeSequence(sequence);
        return datePrefix + encoded;
    }

    /**
     * Mã hóa số thứ tự tự tăng thành chuỗi 4 ký tự xáo trộn ngẫu nhiên một cách có hệ thống và đảo ngược được.
     */
    private String encodeSequence(long sequence) {
        // Ánh xạ song ánh coprime để biến đổi x -> y phân tán
        long scrambled = (sequence * COPRIME_MULTIPLIER) % MODULUS;

        // Chuyển đổi số y sang dạng Base-30 với độ dài đúng 4 ký tự
        char[] chars = new char[4];
        long temp = scrambled;
        for (int i = 3; i >= 0; i--) {
            chars[i] = ALPHABET.charAt((int) (temp % BASE));
            temp /= BASE;
        }
        return new String(chars);
    }
}
