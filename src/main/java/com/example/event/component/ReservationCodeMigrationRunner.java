package com.example.event.component;

import com.example.event.entity.Reservation;
import com.example.event.repository.ReservationRepository;
import com.example.event.util.ReservationCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationCodeMigrationRunner implements CommandLineRunner {

    private final ReservationRepository reservationRepository;
    private final ReservationCodeGenerator reservationCodeGenerator;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("[MIGRATION] Bắt đầu kiểm tra mã đơn hàng (code) cho dữ liệu cũ...");

        List<Reservation> nullCodeReservations = reservationRepository.findByCodeIsNull();
        if (nullCodeReservations.isEmpty()) {
            log.info("[MIGRATION] Không có đơn hàng cũ nào cần bổ sung mã code. Hệ thống sạch sẽ!");
            return;
        }

        log.info("[MIGRATION] Tìm thấy {} đơn hàng cũ chưa có mã code. Tiến hành sinh mã di cư dữ liệu...", nullCodeReservations.size());

        // Sử dụng Map để theo dõi số thứ tự tự tăng của từng ngày cụ thể phục vụ migration dữ liệu cũ
        Map<String, Integer> dailySequenceMap = new HashMap<>();
        DateTimeFormatter keyFormatter = DateTimeFormatter.ofPattern("yyMMdd");

        int successCount = 0;
        for (Reservation reservation : nullCodeReservations) {
            try {
                // Xác định ngày đặt hàng thực tế, mặc định lấy ngày hiện tại nếu null
                LocalDate date = reservation.getCreatedAt() != null 
                        ? reservation.getCreatedAt().toLocalDate() 
                        : LocalDate.now();

                String dateKey = date.format(keyFormatter);
                
                // Tăng số thứ tự của ngày này lên 1
                int seq = dailySequenceMap.getOrDefault(dateKey, 0) + 1;
                dailySequenceMap.put(dateKey, seq);

                // Sinh mã đơn hàng dựa trên ngày thực tế của đơn hàng đó
                String orderCode = reservationCodeGenerator.generateCodeForDate(date, seq);
                reservation.setCode(orderCode);
                
                reservationRepository.save(reservation);
                successCount++;
            } catch (Exception e) {
                log.error("[MIGRATION] Lỗi khi sinh mã cho đơn hàng ID {}: {}", reservation.getId(), e.getMessage());
            }
        }

        reservationRepository.flush();
        log.info("[MIGRATION] Hoàn tất! Đã cập nhật thành công mã code cho {}/{} đơn hàng cũ!", successCount, nullCodeReservations.size());
    }
}
