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

            helper.setFrom(fromEmail);
            helper.setTo(first.getUserEmail());
            helper.setSubject("Xác nhận vé — " + first.getEventName());
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
        String name      = nvl(f.getUserFullName(), "Quý khách");
        String eventName = nvl(f.getEventName(), "sự kiện");
        String location  = nvl(f.getEventLocation(), "—");
        String showTime  = parseDt(f.getShowStartTime());
        String ref       = nvl(f.getReservationCode(), "—");
        String veWord    = totalTickets > 1 ? totalTickets + " vé" : "01 vé";

        return """
            <!DOCTYPE html><html lang="vi"><head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            <style>
              body{margin:0;padding:0;background:#f9fafb;font-family:-apple-system,'Helvetica Neue',Arial,sans-serif;color:#191a1a}
              .wrap{max-width:560px;margin:40px auto;background:#fff;border-radius:16px;overflow:hidden;
                    box-shadow:0 4px 24px rgba(0,0,0,.08);border:1px solid #e2e2e2}
              .hdr{background:#191a1a;padding:28px 32px 20px;text-align:center}
              .hdr h1{margin:0;font-size:20px;font-weight:800;color:#fff;letter-spacing:-.5px}
              .hdr p{margin:6px 0 0;font-size:12px;color:#c6c6c7}
              .body{padding:28px 32px}
              .greeting{font-size:15px;font-weight:700;margin-bottom:6px}
              .intro{font-size:13px;color:#474848;line-height:1.6;margin-bottom:22px}
              .badge{display:inline-block;background:#e8f0fe;color:#1967d2;font-size:10px;font-weight:800;
                     letter-spacing:.1em;text-transform:uppercase;padding:4px 12px;border-radius:99px;margin-bottom:16px}
              .card{background:#f7f7f7;border-radius:12px;padding:18px 22px;border:1px solid #e2e2e2;margin-bottom:20px}
              .row{display:flex;justify-content:space-between;margin-bottom:10px}
              .row:last-child{margin-bottom:0}
              .lb{font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:#777778}
              .vl{font-size:13px;font-weight:700;color:#191a1a;text-align:right;max-width:60%%}
              .divider{border:none;border-top:1px dashed #c7c6c6;margin:20px 0}
              .note{font-size:13px;color:#474848;line-height:1.6}
              .footer{background:#f1f1f1;padding:18px 32px;text-align:center;font-size:11px;color:#777778}
            </style></head><body>
            <div class="wrap">
              <div class="hdr">
                <h1>EventHunting</h1>
                <p>Nền tảng mua vé sự kiện hàng đầu Việt Nam</p>
              </div>
              <div class="body">
                <p class="greeting">Xin chào, %s!</p>
                <p class="intro">
                  Cảm ơn bạn đã đặt vé. Đơn hàng của bạn cho sự kiện
                  <strong>%s</strong> đã được xác nhận.<br/>
                  <strong>%s</strong> được đính kèm trong email này. Vui lòng xuất trình vé khi vào cửa.
                </p>
                <div style="text-align:center"><span class="badge">Đã xác nhận</span></div>
                <div class="card">
                  <div class="row"><span class="lb">Sự kiện</span><span class="vl">%s</span></div>
                  <div class="row"><span class="lb">Thời gian</span><span class="vl">%s</span></div>
                  <div class="row"><span class="lb">Địa điểm</span><span class="vl">%s</span></div>
                  <div class="row"><span class="lb">Mã đơn hàng</span><span class="vl">%s</span></div>
                  <div class="row"><span class="lb">Số lượng vé</span><span class="vl">%s</span></div>
                </div>
                <hr class="divider"/>
                <p class="note">
                  📎 Mỗi file PDF đính kèm là một vé riêng biệt với mã QR duy nhất.<br/>
                  Tải về và xuất trình từng vé tại quầy check-in.
                </p>
              </div>
              <div class="footer">© 2025 EventHunting. Mọi quyền được bảo lưu.</div>
            </div>
            </body></html>
            """.formatted(name, eventName, veWord, eventName, showTime, location, ref, veWord);
    }

    private String parseDt(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        try { return LocalDateTime.parse(raw).format(DT); }
        catch (Exception e) { return raw; }
    }



    private String nvl(String s, String fb) { return (s == null || s.isBlank()) ? fb : s; }
}
