package com.example.event.component;

import com.example.event.config.RedisStreamConfig;
import com.example.event.dto.TicketEmailMessage;
import com.example.event.service.TicketMailService;
import com.example.event.service.TicketPdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumes batch messages from the Redis Stream "stream:ticket-email".
 *
 * Flow per message (one reservation):
 * 1. Parse fields → List<TicketEmailMessage>
 * 2. Check Idempotency via Business Key (reservationId) in Redis
 * 3. Generate PDFs for each ticket
 * 4. Send one email with all PDFs attached
 * 5. Mark Idempotency as SENT and ACK
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEmailConsumer
        implements StreamListener<String, MapRecord<String, String, String>> {

    private final TicketPdfService ticketPdfService;
    private final TicketMailService ticketMailService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String IDEMPOTENCY_KEY_FORMAT = "idempotency:email:ticket:%s";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SENT = "SENT";

    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration COMPLETED_TTL = Duration.ofDays(7);

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String recordId = record.getId().getValue();
        log.info("[TICKET-CONSUMER] Bắt đầu xử lý batch record={}", recordId);

        String idempotencyKey = null;
        try {
            // 1. Parse batch tickets from Map
            List<TicketEmailMessage> tickets = TicketEmailMessage.fromBatchMap(record.getValue());
            if (tickets.isEmpty()) {
                log.warn("[TICKET-CONSUMER] Batch record {} rỗng, tiến hành ACK.", recordId);
                ack(record);
                return;
            }

            String reservationId = tickets.get(0).getReservationId();
            if (reservationId == null || reservationId.isBlank()) {
                log.error("[TICKET-CONSUMER] Thiếu reservationId trong record={}, tiến hành ACK để bỏ qua.", recordId);
                ack(record);
                return;
            }

            idempotencyKey = String.format(IDEMPOTENCY_KEY_FORMAT, reservationId);

            // 2. Kiểm tra Idempotency bằng atomic SETNX
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, STATUS_PROCESSING, LOCK_TIMEOUT);

            if (Boolean.FALSE.equals(acquired)) {
                String currentStatus = stringRedisTemplate.opsForValue().get(idempotencyKey);
                if (STATUS_SENT.equals(currentStatus)) {
                    log.info("[TICKET-CONSUMER] Email cho reservationId={} đã được gửi thành công trước đó. Bỏ qua và ACK record={}",
                            reservationId, recordId);
                    ack(record);
                    return;
                } else if (STATUS_PROCESSING.equals(currentStatus)) {
                    log.warn("[TICKET-CONSUMER] Đơn hàng reservationId={} đang được worker khác xử lý. Bỏ qua record={}",
                            reservationId, recordId);
                    return;
                }
            }

            log.info("[TICKET-CONSUMER] Đang xử lý {} vé cho đơn hàng {}",
                    tickets.size(), reservationId);

            // 3. Generate PDF for each ticket
            List<byte[]> pdfList = new ArrayList<>();
            for (TicketEmailMessage ticket : tickets) {
                pdfList.add(ticketPdfService.generatePdf(ticket));
            }

            // 4. Send one email with all PDFs
            ticketMailService.sendAllTickets(tickets, pdfList);

            // 5. Đánh dấu trạng thái đã gửi thành công (giữ 7 ngày)
            stringRedisTemplate.opsForValue().set(idempotencyKey, STATUS_SENT, COMPLETED_TTL);

            // 6. ACK on success
            ack(record);
            log.info("[TICKET-CONSUMER] Xử lý thành công record={} cho đơn hàng {}",
                    recordId, reservationId);

        } catch (Exception e) {
            log.error("[TICKET-CONSUMER] Xử lý thất bại batch record={}: {}",
                    recordId, e.getMessage(), e);
            // Nếu lỗi xảy ra khi đang xử lý, xóa key PROCESSING để cho phép retry
            if (idempotencyKey != null) {
                try {
                    String status = stringRedisTemplate.opsForValue().get(idempotencyKey);
                    if (STATUS_PROCESSING.equals(status)) {
                        stringRedisTemplate.delete(idempotencyKey);
                    }
                } catch (Exception ex) {
                    log.error("[TICKET-CONSUMER] Lỗi khi giải phóng lock idempotency cho key={}: {}", idempotencyKey, ex.getMessage());
                }
            }
        }
    }

    private void ack(MapRecord<String, String, String> record) {
        stringRedisTemplate.opsForStream().acknowledge(
                RedisStreamConfig.STREAM_KEY,
                RedisStreamConfig.GROUP_NAME,
                record.getId()
        );
    }
}
