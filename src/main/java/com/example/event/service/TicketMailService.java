package com.example.event.service;

import com.example.event.dto.TicketEmailMessage;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Gửi 1 email xác nhận đặt vé với tất cả file PDF đính kèm.
 * Mỗi vé = 1 file PDF: ticket-{ticketId}.pdf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketMailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy", new Locale("vi", "VN"));

    /**
     * @param tickets  danh sách TicketEmailMessage (dùng để lấy ticketId cho tên file)
     * @param pdfList  danh sách byte[] tương ứng từng vé
     */
    public void sendAllTickets(List<TicketEmailMessage> tickets, List<byte[]> pdfList) {
        if (tickets.isEmpty()) return;
        TicketEmailMessage first = tickets.get(0);
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

            helper.setFrom(fromEmail, "Event Hunting");
            helper.setTo(first.getUserEmail());
            helper.setSubject("Xác nhận vé điện tử: " + first.getEventName() + " — Event Hunting");
            helper.setText(buildHtml(first, tickets.size()), true);

            // Đính kèm từng vé
            for (int i = 0; i < tickets.size(); i++) {
                String fileName = "ve-" + (i + 1) + "-" + tickets.get(i).getTicketId() + ".pdf";
                helper.addAttachment(fileName, new ByteArrayResource(pdfList.get(i)), "application/pdf");
            }

            mailSender.send(mime);
            log.info("[TICKET-MAIL] Gửi email {} vé → {} | event={}",
                    tickets.size(), first.getUserEmail(), first.getEventName());
        } catch (Exception e) {
            log.error("[TICKET-MAIL] Lỗi gửi email → {}: {}", first.getUserEmail(), e.getMessage(), e);
            throw new RuntimeException("Gửi email thất bại", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String buildHtml(TicketEmailMessage f, int totalTickets) {
        String name        = nvl(f.getUserFullName(), "Quý khách");
        String eventName   = nvl(f.getEventName(), "sự kiện");
        String location    = nvl(f.getEventLocation(), "—");
        String showTime    = parseDt(f.getShowStartTime());
        String ref         = nvl(f.getReservationCode(), "—");
        String veWord      = totalTickets > 1 ? totalTickets + " vé" : "01 vé";
        int currentYear    = java.time.Year.now().getValue();

        return "<div style=\"background-color: #f8fafc; padding: 40px 15px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\">" +
                "  <div style=\"max-width: 560px; margin: 0 auto; background-color: #ffffff; border-radius: 18px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.06); border: 1px solid #e2e8f0;\">" +
                "    <!-- Header Banner -->" +
                "    <div style=\"background: linear-gradient(135deg, #10b981 0%, #059669 100%); padding: 32px 24px; text-align: center;\">" +
                "      <h1 style=\"color: #ffffff; margin: 0; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;\">Event Hunting</h1>" +
                "      <p style=\"color: rgba(255,255,255,0.92); margin: 6px 0 0 0; font-size: 14.5px;\">Xác nhận đặt vé thành công</p>" +
                "    </div>" +
                "    <!-- Body Content -->" +
                "    <div style=\"padding: 32px 28px; color: #334155; line-height: 1.6;\">" +
                "      <p style=\"font-size: 15px; margin-top: 0;\">Xin chào <strong>" + name + "</strong>,</p>" +
                "      <p style=\"font-size: 14.5px; color: #475569;\">Cảm ơn bạn đã tin tưởng đặt vé tại <strong>Event Hunting</strong>. Đơn hàng của bạn cho sự kiện <strong>" + eventName + "</strong> đã được thanh toán và xác nhận thành công!</p>" +
                "      <!-- Status Badge -->" +
                "      <div style=\"text-align: center; margin: 20px 0;\">" +
                "        <span style=\"display: inline-block; background-color: #ecfdf5; color: #059669; border: 1px solid #a7f3d0; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.8px; padding: 6px 20px; border-radius: 9999px;\">✓ Đã xác nhận đặt vé</span>" +
                "      </div>" +
                "      <!-- Ticket Info Box -->" +
                "      <div style=\"background-color: #f8fafc; border-radius: 14px; padding: 20px 22px; border: 1px solid #e2e8f0; margin: 24px 0;\">" +
                "        <table style=\"width: 100%; border-collapse: collapse; font-size: 14px;\">" +
                "          <tr>" +
                "            <td style=\"padding: 8px 0; color: #64748b; font-weight: 600; width: 35%;\">Sự kiện:</td>" +
                "            <td style=\"padding: 8px 0; color: #1e293b; font-weight: 700; text-align: right;\">" + eventName + "</td>" +
                "          </tr>" +
                "          <tr>" +
                "            <td style=\"padding: 8px 0; color: #64748b; font-weight: 600; border-top: 1px solid #f1f5f9;\">Thời gian:</td>" +
                "            <td style=\"padding: 8px 0; color: #1e293b; font-weight: 700; text-align: right; border-top: 1px solid #f1f5f9;\">" + showTime + "</td>" +
                "          </tr>" +
                "          <tr>" +
                "            <td style=\"padding: 8px 0; color: #64748b; font-weight: 600; border-top: 1px solid #f1f5f9;\">Địa điểm:</td>" +
                "            <td style=\"padding: 8px 0; color: #1e293b; font-weight: 700; text-align: right; border-top: 1px solid #f1f5f9;\">" + location + "</td>" +
                "          </tr>" +
                "          <tr>" +
                "            <td style=\"padding: 8px 0; color: #64748b; font-weight: 600; border-top: 1px solid #f1f5f9;\">Mã đơn hàng:</td>" +
                "            <td style=\"padding: 8px 0; color: #059669; font-weight: 800; font-family: monospace; font-size: 15px; text-align: right; border-top: 1px solid #f1f5f9;\">" + ref + "</td>" +
                "          </tr>" +
                "          <tr>" +
                "            <td style=\"padding: 8px 0; color: #64748b; font-weight: 600; border-top: 1px solid #f1f5f9;\">Số lượng:</td>" +
                "            <td style=\"padding: 8px 0; color: #1e293b; font-weight: 700; text-align: right; border-top: 1px solid #f1f5f9;\">" + veWord + "</td>" +
                "          </tr>" +
                "        </table>" +
                "      </div>" +
                "      <!-- PDF Attachment Notice -->" +
                "      <div style=\"background-color: #ecfdf5; border-left: 4px solid #10b981; border-radius: 6px; padding: 14px 18px; margin: 24px 0;\">" +
                "        <p style=\"margin: 0; font-size: 13.5px; color: #065f46; line-height: 1.5;\">" +
                "          📎 <strong>File vé điện tử (PDF):</strong> <strong>" + veWord + "</strong> đã được đính kèm trực tiếp trong email này. Mỗi vé đều chứa một mã QR duy nhất để check-in tại quầy soát vé." +
                "        </p>" +
                "      </div>" +
                "      <p style=\"font-size: 13px; color: #94a3b8; margin-bottom: 0;\">Nếu bạn có bất kỳ thắc mắc nào, vui lòng liên hệ đội ngũ hỗ trợ của chúng tôi để được giải đáp nhanh chóng.</p>" +
                "    </div>" +
                "    <!-- Footer -->" +
                "    <div style=\"background-color: #f1f5f9; padding: 18px 24px; text-align: center; border-top: 1px solid #e2e8f0;\">" +
                "      <p style=\"margin: 0; font-size: 12px; color: #94a3b8;\">© " + currentYear + " Event Hunting. All rights reserved.</p>" +
                "    </div>" +
                "  </div>" +
                "</div>";
    }

    private String parseDt(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "—";
        try { return LocalDateTime.parse(raw).format(DT); }
        catch (Exception e) { return raw; }
    }



    private String nvl(String s, String fb) { return (s == null || s.trim().isEmpty()) ? fb : s; }
}
