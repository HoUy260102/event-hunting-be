package com.example.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO per-ticket dùng cho PDF generation.
 * Stream Redis dùng batch: 1 entry = 1 đơn hàng = N tickets.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketEmailMessage {

    private String ticketId;
    private String reservationId;
    private String reservationCode;
    private String unitPrice;
    private String section;
    private String seatLabel;
    private String displayName;
    private String qrCode;
    private String status;
    private String showStartTime;
    private String showEndTime;
    private String eventName;
    private String eventLocation;
    private String userEmail;
    private String userFullName;

    // ── Batch: 1 stream entry = 1 reservation (N tickets) ─────────────────

    public static Map<String, String> toBatchMap(String reservationId,
                                                 String reservationCode,
                                                 String userEmail,
                                                 String userFullName,
                                                 List<TicketEmailMessage> tickets) {
        Map<String, String> m = new HashMap<>();
        m.put("reservationId",  nvl(reservationId));
        m.put("reservationCode",nvl(reservationCode));
        m.put("userEmail",      nvl(userEmail));
        m.put("userFullName",   nvl(userFullName));
        m.put("ticketCount",    String.valueOf(tickets.size()));

        // Chia sẻ từ ticket đầu tiên (tất cả cùng sự kiện)
        if (!tickets.isEmpty()) {
            m.put("eventName",     nvl(tickets.get(0).getEventName()));
            m.put("eventLocation", nvl(tickets.get(0).getEventLocation()));
            m.put("showStartTime", nvl(tickets.get(0).getShowStartTime()));
            m.put("showEndTime",   nvl(tickets.get(0).getShowEndTime()));
        }

        for (int i = 0; i < tickets.size(); i++) {
            TicketEmailMessage t = tickets.get(i);
            String p = "t" + i + "_";
            m.put(p + "id",            nvl(t.getTicketId()));
            m.put(p + "unitPrice",     nvl(t.getUnitPrice()));
            m.put(p + "section",       nvl(t.getSection()));
            m.put(p + "seatLabel",     nvl(t.getSeatLabel()));
            m.put(p + "displayName",   nvl(t.getDisplayName()));
            m.put(p + "qrCode",        nvl(t.getQrCode()));
            m.put(p + "status",        nvl(t.getStatus()));
        }
        return m;
    }

    public static List<TicketEmailMessage> fromBatchMap(Map<String, String> m) {
        String reservationId   = m.get("reservationId");
        String reservationCode = m.get("reservationCode");
        String userEmail       = m.get("userEmail");
        String userFullName    = m.get("userFullName");
        String eventName       = m.get("eventName");
        String eventLocation   = m.get("eventLocation");
        String showStartTime   = m.get("showStartTime");
        String showEndTime     = m.get("showEndTime");

        int count = 0;
        try { count = Integer.parseInt(m.getOrDefault("ticketCount", "0")); }
        catch (NumberFormatException ignored) {}

        List<TicketEmailMessage> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String p = "t" + i + "_";
            list.add(TicketEmailMessage.builder()
                    .ticketId(m.get(p + "id"))
                    .reservationId(reservationId)
                    .reservationCode(reservationCode)
                    .unitPrice(m.get(p + "unitPrice"))
                    .section(m.get(p + "section"))
                    .seatLabel(m.get(p + "seatLabel"))
                    .displayName(m.get(p + "displayName"))
                    .qrCode(m.get(p + "qrCode"))
                    .status(m.get(p + "status"))
                    .showStartTime(showStartTime)
                    .showEndTime(showEndTime)
                    .eventName(eventName)
                    .eventLocation(eventLocation)
                    .userEmail(userEmail)
                    .userFullName(userFullName)
                    .build());
        }
        return list;
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
