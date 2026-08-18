package com.example.event.service;

import com.example.event.dto.TicketEmailMessage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.Phrase;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class TicketPdfService {

    private static final Color BG_SURFACE   = new Color(0x0E, 0x0E, 0x0E); // Dark page BG
    private static final Color BG_WHITE     = Color.WHITE;
    private static final Color BG_HEADER    = new Color(0xF1, 0xF5, 0xF9); // Soft slate header BG
    private static final Color BG_CREAM     = new Color(0xFC, 0xF9, 0xF8); // Warm soft cream BG for stub
    private static final Color TEXT_MAIN    = new Color(0x0F, 0x17, 0x2A); // Slate-900 for event title
    private static final Color TEXT_SLATE   = new Color(0x1E, 0x29, 0x3B); // Slate-800 for value texts
    private static final Color TEXT_SUB     = new Color(0x64, 0x74, 0x8B); // Slate-500 for label texts
    private static final Color COLOR_BORDER = new Color(0xE2, 0xE8, 0xF0); // Light border #E2E8F0
    private static final Color BADGE_BG     = new Color(0xDC, 0xFC, 0xE7); // Light green bg
    private static final Color BADGE_FG     = new Color(0x15, 0x80, 0x3D); // Dark green fg
    private static final Color BADGE_BORDER = new Color(0xA7, 0xF3, 0xD0); // Light green border

    private static final float PAGE_W  = 370f; // Slender, premium smartphone pass width
    private static final float PAGE_H  = 480f; // Tighter, extremely elegant page height
    private static final float MARGIN  = 20f;
    private static final float INNER_W = PAGE_W - MARGIN * 2;
    private static final float PAD     = 16f;
    private static final float HEADER_H = 75f;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("HH:mm  dd/MM/yyyy");
    private static final NumberFormat VND;
    static { VND = NumberFormat.getNumberInstance(new Locale("vi", "VN")); }

    private final BaseFont vnFont;
    public TicketPdfService() { vnFont = loadFont(); }

    private static BaseFont loadFont() {
        for (String p : new String[]{
                "C:/Windows/Fonts/arial.ttf",
                "C:/Windows/Fonts/verdana.ttf",
                "C:/Windows/Fonts/tahoma.ttf"}) {
            try { return BaseFont.createFont(p, BaseFont.IDENTITY_H, BaseFont.EMBEDDED); }
            catch (Exception ignored) {}
        }
        try { return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED); }
        catch (Exception e) { throw new RuntimeException("Không load được font", e); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    public byte[] generatePdf(TicketEmailMessage msg) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(new Rectangle(PAGE_W, PAGE_H), 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();
            PdfContentByte cb = writer.getDirectContent();

            // ── 1. Nền trang tối (BG_SURFACE) ───────────────────────────────
            fillRect(cb, 0, 0, PAGE_W, PAGE_H, BG_SURFACE);

            // ── 2. Vẽ nền Card chính (BG_WHITE) ───────────────────────────────
            float cardR = 20f;
            roundedRect(cb, MARGIN, MARGIN, INNER_W, PAGE_H - MARGIN * 2, cardR, BG_WHITE, COLOR_BORDER);

            // ── 3. Vẽ nền Stub cuống vé (BG_CREAM) ─────────────────────────────
            float tearY = 185f; // Điểm nét đứt phân chia vé cực kỳ cân đối
            roundedRect(cb, MARGIN, MARGIN, INNER_W, tearY - MARGIN, cardR, BG_CREAM, null);
            fillRect(cb, MARGIN, tearY - 20f, INNER_W, 20f, BG_CREAM); // làm phẳng cạnh tiếp giáp nét đứt

            // ── 4. Vẽ nền Header (BG_HEADER) ──────────────────────────────────
            float headerY = PAGE_H - MARGIN - HEADER_H;
            roundedRect(cb, MARGIN, headerY, INNER_W, HEADER_H, cardR, BG_HEADER, null);
            fillRect(cb, MARGIN, headerY, INNER_W, 20f, BG_HEADER); // làm phẳng cạnh dưới header
            
            // Vẽ lại viền toàn bộ card đè lên để đảm bảo nét vẽ viền sắc nét
            roundedRect(cb, MARGIN, MARGIN, INNER_W, PAGE_H - MARGIN * 2, cardR, null, COLOR_BORDER);
            
            // Đường phân cách mỏng dưới header
            hLine(cb, MARGIN, headerY, INNER_W, COLOR_BORDER);

            float y = PAGE_H - MARGIN; // Con trỏ tọa độ dọc (bắt đầu từ đỉnh 460)

            // ── 5. Tiêu đề Sự kiện & Nhãn ĐÃ XÁC NHẬN trong Header ──────────────
            y -= 18f; // Khoảng cách đệm phía trên
            PdfPTable hdrTbl = new PdfPTable(new float[]{2.5f, 1f});
            hdrTbl.setTotalWidth(INNER_W - PAD * 2);
            
            // Cột trái: Tên sự kiện
            PdfPCell nameCell = new PdfPCell();
            nameCell.setBorder(Rectangle.NO_BORDER);
            nameCell.setPadding(0);
            Paragraph nameP = new Paragraph(nvl(msg.getEventName(), "Sự kiện"), new Font(vnFont, 13f, Font.BOLD, TEXT_MAIN));
            nameP.setLeading(15f);
            nameCell.addElement(nameP);
            hdrTbl.addCell(nameCell);
            
            // Cột phải: Chỉ làm cột đệm để căn lề
            PdfPCell badgeCell = new PdfPCell();
            badgeCell.setBorder(Rectangle.NO_BORDER);
            badgeCell.setPadding(0);
            hdrTbl.addCell(badgeCell);
            
            hdrTbl.calculateHeights(false);
            hdrTbl.writeSelectedRows(0, -1, MARGIN + PAD, y, cb);

            // Vẽ Huy hiệu ĐÃ XÁC NHẬN bo tròn góc cực kỳ sắc nét
            float badgeW = 75f, badgeH = 16f;
            float badgeX = MARGIN + INNER_W - PAD - badgeW;
            float badgeY = y - 22f; // Căn giữa dọc ngang hàng với tiêu đề
            
            roundedRect(cb, badgeX, badgeY, badgeW, badgeH, 8f, BADGE_BG, BADGE_BORDER);
            
            PdfPTable badgeSub = oneCellTable("ĐÃ XÁC NHẬN", new Font(vnFont, 7f, Font.BOLD, BADGE_FG), null, badgeW, 3f, 0f, 3f, 0f);
            badgeSub.getRow(0).getCells()[0].setHorizontalAlignment(Element.ALIGN_CENTER);
            badgeSub.calculateHeights(false);
            badgeSub.writeSelectedRows(0, -1, badgeX, badgeY + badgeH, cb);
            
            // Con trỏ di chuyển xuống dưới header
            y = headerY - 8f;

            // ── 6. Lưới Thông tin Vé (Tối ưu hóa khoảng cách cực kỳ gọn gàng) ───
            Font lFont = new Font(vnFont, 7f, Font.BOLD, TEXT_SUB);
            Font vFont = new Font(vnFont, 9.5f,  Font.BOLD, TEXT_SLATE);

            PdfPTable info = new PdfPTable(2);
            info.setTotalWidth(INNER_W - PAD * 2);
            info.setWidths(new float[]{1f, 1f});

            info.addCell(infoCell("KHÁCH HÀNG",       nvl(msg.getUserFullName(), "—"), lFont, vFont));
            info.addCell(infoCell("MÃ ĐƠN HÀNG",      nvl(msg.getReservationCode(), "—"), lFont, vFont));
            info.addCell(infoCell("THỜI GIAN",         parseDt(msg.getShowStartTime()), lFont, vFont));
            info.addCell(infoCell("VỊ TRÍ / KHU VỰC", seatStr(msg), lFont, vFont));

            PdfPCell venueCell = infoCell("ĐỊA ĐIỂM", nvl(msg.getEventLocation(), "—"), lFont, vFont);
            venueCell.setColspan(2);
            info.addCell(venueCell);

            PdfPCell priceCell = infoCell("GIÁ VÉ", fmtPrice(msg.getUnitPrice()), lFont, vFont);
            priceCell.setColspan(2);
            info.addCell(priceCell);

            info.calculateHeights(false);
            info.writeSelectedRows(0, -1, MARGIN + PAD, y, cb);

            // ── 7. Đường răng cưa nét đứt & 2 góc cắt khuyết (Notches) ──────────
            circle(cb, MARGIN,          tearY, 10f, BG_SURFACE);
            circle(cb, MARGIN + INNER_W, tearY, 10f, BG_SURFACE);
            
            cb.saveState();
            cb.setLineDash(6f, 4f, 0f);
            cb.setColorStroke(COLOR_BORDER);
            cb.setLineWidth(1.5f);
            cb.moveTo(MARGIN + 10f, tearY);
            cb.lineTo(MARGIN + INNER_W - 10f, tearY);
            cb.stroke();
            cb.restoreState();

            // ── 8. Hộp chứa QR Code màu trắng ở phần cuống vé ───────────────────
            byte[] qrBytes = genQr(nvl(msg.getQrCode(), msg.getTicketId()), 200);
            Image qrImg    = Image.getInstance(qrBytes);
            
            float qrSz = 90f, pad = 8f;
            float boxW = qrSz + pad * 2, boxH = qrSz + pad * 2;
            float boxX = (PAGE_W - boxW) / 2f;
            float boxY = 52f; // Căn giữa dọc cực đẹp trong phần cuống vé 165f

            roundedRect(cb, boxX, boxY, boxW, boxH, 10f, Color.WHITE, COLOR_BORDER);
            qrImg.scaleAbsolute(qrSz, qrSz);
            qrImg.setAbsolutePosition(boxX + pad, boxY + pad);
            cb.addImage(qrImg);

            // ── 9. Chữ hướng dẫn dưới QR Code ──────────────────────────────────
            Font scanB = new Font(vnFont, 7.5f, Font.BOLD, TEXT_SUB);
            PdfPTable scanTbl = new PdfPTable(1);
            scanTbl.setTotalWidth(INNER_W - PAD * 2);
            scanTbl.addCell(centreCell("QUÉT MÃ TẠI CỬA VÀO", scanB));
            
            scanTbl.calculateHeights(false);
            scanTbl.writeSelectedRows(0, -1, MARGIN + PAD, boxY - 12f, cb);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("[PDF] Lỗi tạo PDF vé {}: {}", msg.getTicketId(), e.getMessage(), e);
            throw new RuntimeException("Tạo PDF thất bại", e);
        }
    }

    // ── Cell helpers ─────────────────────────────────────────────────────────

    private PdfPTable oneCellTable(String text, Font font, Color bg, float width,
                                   float padTop, float padLeft, float padBottom, float padRight) {
        PdfPTable t = new PdfPTable(1);
        t.setTotalWidth(width);
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(padTop); c.setPaddingLeft(padLeft);
        c.setPaddingBottom(padBottom); c.setPaddingRight(padRight);
        t.addCell(c);
        return t;
    }

    private PdfPCell infoCell(String label, String value, Font lFont, Font vFont) {
        PdfPTable inner = new PdfPTable(1);
        inner.setWidthPercentage(100);
        PdfPCell lb = new PdfPCell(new Phrase(label, lFont));
        lb.setBorder(Rectangle.NO_BORDER); lb.setPaddingBottom(1.5f);
        inner.addCell(lb);
        PdfPCell vl = new PdfPCell(new Phrase(value, vFont));
        vl.setBorder(Rectangle.NO_BORDER);
        inner.addCell(vl);
        PdfPCell wrapper = new PdfPCell(inner);
        wrapper.setBorder(Rectangle.NO_BORDER);
        wrapper.setPadding(4f); // Tighter padding to eliminate blank spaces
        return wrapper;
    }

    private PdfPCell centreCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPaddingBottom(4f);
        return c;
    }

    // ── Shape helpers ────────────────────────────────────────────────────────

    private void fillRect(PdfContentByte cb, float x, float y, float w, float h, Color fill) {
        cb.saveState();
        cb.setColorFill(fill);
        cb.rectangle(x, y, w, h);
        cb.fill();
        cb.restoreState();
    }

    private void roundedRect(PdfContentByte cb, float x, float y, float w, float h,
                              float r, Color fill, Color stroke) {
        cb.saveState();
        if (fill   != null) cb.setColorFill(fill);
        if (stroke != null) { cb.setColorStroke(stroke); cb.setLineWidth(0.5f); }
        cb.roundRectangle(x, y, w, h, r);
        if (fill != null && stroke != null) cb.fillStroke();
        else if (fill != null)              cb.fill();
        else                                cb.stroke();
        cb.restoreState();
    }

    private void circle(PdfContentByte cb, float cx, float cy, float r, Color fill) {
        cb.saveState();
        cb.setColorFill(fill);
        cb.circle(cx, cy, r);
        cb.fill();
        cb.restoreState();
    }

    private void hLine(PdfContentByte cb, float x, float y, float w, Color c) {
        cb.saveState();
        cb.setColorStroke(c); cb.setLineWidth(0.5f);
        cb.moveTo(x, y); cb.lineTo(x + w, y); cb.stroke();
        cb.restoreState();
    }

    // ── QR ───────────────────────────────────────────────────────────────────

    private byte[] genQr(String content, int size) throws Exception {
        Map<EncodeHintType, Object> h = new EnumMap<>(EncodeHintType.class);
        h.put(EncodeHintType.MARGIN, 1);
        h.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix bm = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, h);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bm, "PNG", out);
        return out.toByteArray();
    }

    // ── Format helpers ────────────────────────────────────────────────────────

    private String parseDt(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "—";
        try { return LocalDateTime.parse(raw).format(DT_FMT); }
        catch (Exception e) { return raw; }
    }

    private String seatStr(TicketEmailMessage m) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(m.getDisplayName())) sb.append(m.getDisplayName());
        if (notBlank(m.getSection()))    { if (sb.length() > 0) sb.append("\n"); sb.append(m.getSection()); }
        if (notBlank(m.getSeatLabel()))  { if (sb.length() > 0) sb.append(" ");  sb.append(m.getSeatLabel()); }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    private String fmtPrice(String raw) {
        try { return VND.format(Long.parseLong(raw)) + " VND"; }
        catch (Exception e) { return nvl(raw, "—"); }
    }

    private String nvl(String s, String fb) { return (s == null || s.trim().isEmpty()) ? fb : s; }
    private boolean notBlank(String s)       { return s != null && !s.trim().isEmpty(); }
}
