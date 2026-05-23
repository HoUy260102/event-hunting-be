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

import java.util.ArrayList;
import java.util.List;

/**
 * Consumes batch messages from the Redis Stream "stream:ticket-email".
 *
 * Flow per message (one reservation):
 *   1. Parse fields → List<TicketEmailMessage>
 *   2. Generate PDFs for each ticket
 *   3. Send one email with all PDFs attached
 *   4. ACK on success
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEmailConsumer
        implements StreamListener<String, MapRecord<String, String, String>> {

    private final TicketPdfService     ticketPdfService;
    private final TicketMailService    ticketMailService;
    private final StringRedisTemplate  stringRedisTemplate;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String recordId = record.getId().getValue();
        log.info("[TICKET-CONSUMER] Processing batch record={}", recordId);

        try {
            // 1. Parse batch tickets from Map
            List<TicketEmailMessage> tickets = TicketEmailMessage.fromBatchMap(record.getValue());
            if (tickets.isEmpty()) {
                log.warn("[TICKET-CONSUMER] Batch record {} is empty, ACKing.", recordId);
                ack(record);
                return;
            }

            log.info("[TICKET-CONSUMER] Processing {} tickets for reservation {}",
                    tickets.size(), tickets.get(0).getReservationId());

            // 2. Generate PDF for each ticket
            List<byte[]> pdfList = new ArrayList<>();
            for (TicketEmailMessage ticket : tickets) {
                pdfList.add(ticketPdfService.generatePdf(ticket));
            }

            // 3. Send one email with all PDFs
            ticketMailService.sendAllTickets(tickets, pdfList);

            // 4. ACK on success
            ack(record);
            log.info("[TICKET-CONSUMER] Done record={} reservation={}",
                    recordId, tickets.get(0).getReservationId());

        } catch (Exception e) {
            log.error("[TICKET-CONSUMER] FAILED batch record={}: {}",
                    recordId, e.getMessage(), e);
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
