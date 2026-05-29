package com.example.event.component;

import com.example.event.config.RedisStreamConfig;
import com.example.event.dto.TicketEmailMessage;
import com.example.event.dto.TicketSummaryDTO;
import com.example.event.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Đẩy 1 message/đơn hàng lên Redis Stream.
 * Message chứa tất cả vé → consumer gửi 1 email với N file PDF đính kèm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEmailProducer {

    private final StringRedisTemplate stringRedisTemplate;

    public void sendReservationTickets(User user, List<TicketSummaryDTO> tickets) {
        if (tickets == null || tickets.isEmpty()) return;
        try {
            List<TicketEmailMessage> msgs = tickets.stream().map(t ->
                    TicketEmailMessage.builder()
                            .ticketId(t.getId())
                            .reservationId(t.getReservationId())
                            .reservationCode(t.getReservationCode())
                            .unitPrice(t.getUnitPrice() != null ? t.getUnitPrice().toString() : "0")
                            .section(t.getSection())
                            .seatLabel(t.getSeatLabel())
                            .displayName(t.getDisplayName())
                            .qrCode(t.getQrCode())
                            .status(t.getStatus() != null ? t.getStatus().name() : "")
                            .showStartTime(t.getShowStartTime() != null ? t.getShowStartTime().toString() : "")
                            .showEndTime(t.getShowEndTime() != null ? t.getShowEndTime().toString() : "")
                            .eventName(t.getEventName())
                            .eventLocation(t.getEventLocation())
                            .userEmail(user.getEmail())
                            .userFullName(user.getName())
                            .build()
            ).collect(Collectors.toList());

            String reservationId = tickets.get(0).getReservationId();
            String reservationCode = tickets.get(0).getReservationCode();
            Map<String, String> fields = TicketEmailMessage.toBatchMap(
                    reservationId, reservationCode, user.getEmail(), user.getName(), msgs);

            RecordId id = stringRedisTemplate.opsForStream()
                    .add(RedisStreamConfig.STREAM_KEY, fields);
            log.info("[TICKET-PRODUCER] Pushed {} vé (reservation={}) → record={}",
                    tickets.size(), reservationId, id);
        } catch (Exception e) {
            log.error("[TICKET-PRODUCER] Lỗi push stream: {}", e.getMessage(), e);
        }
    }
}
