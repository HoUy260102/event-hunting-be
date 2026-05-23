package com.example.event.service;

import com.example.event.dto.TicketEmailMessage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
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

    private static final Color BG_SURFACE   = new Color(0xF9, 0xFA, 0xFB);
    private static final Color BG_WHITE     = Color.WHITE;
    private static final Color HEADER_DARK  = new Color(0x19, 0x1A, 0x1A);
    private static final Color TEXT_MAIN    = new Color(0x19, 0x1A, 0x1A);
    private static final Color TEXT_SUB     = new Color(0x47, 0x48, 0x48);
    private static final Color COLOR_BORDER = new Color(0xC7, 0xC6, 0xC6);
    private static final Color BADGE_BG     = new Color(0xE8, 0xF0, 0xFE);
    private static final Color BADGE_FG     = new Color(0x19, 0x67, 0xD2);
    private static final Color COLOR_BLACK  = Color.BLACK;

    private static final float PAGE_W  = 420f;
    private static final float PAGE_H  = 630f;
    private static final float MARGIN  = 20f;
    private static final float INNER_W = PAGE_W - MARGIN * 2;
    private static final float PAD     = 16f;
    private static final float HEADER_H = 84f;

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

            // ── Nền trang (màu surface) ───────────────────────────────────
            fillRect(cb, 0, 0, PAGE_W, PAGE_H, BG_SURFACE);

            // ── Card tổng: vẽ viền bo tròn trắng ───────────────────────────────
            float cardR = 20f;
            roundedRect(cb, MARGIN, MARGIN, INNER_W, PAGE_H - MARGIN * 2, cardR, BG_WHITE, COLOR_BORDER);

            // ── Header: Bo góc trên, phẳng ở dưới ───────────────────────────
            float headerY = PAGE_H - MARGIN - HEADER_H;
            // Vẽ phần bo góc (toàn bộ header area)
            roundedRect(cb, MARGIN, headerY, INNER_W, HEADER_H, cardR, HEADER_DARK, null);
            // Vẽ đè một hình chữ nhật phẳng ở đáy header để làm phẳng cạnh dưới (nối vào body)
            fillRect(cb, MARGIN, headerY, INNER_W, 20f, HEADER_DARK);

            float y = PAGE_H - MARGIN; // con trỏ từ trên xuống

            // ── 1. Tên sự kiện trong header ───────────────────────────────
            Font evtHdr = new Font(vnFont, 12f, Font.BOLD, Color.WHITE);
            // set bg = null so it doesn't cover rounded corners
            PdfPTable hdrTbl = oneCellTable(nvl(msg.getEventName(), "Sự kiện"), evtHdr,
                    null, INNER_W, PAD, PAD, 8f, 8f);
            hdrTbl.getDefaultCell().setMinimumHeight(HEADER_H);
            hdrTbl.getDefaultCell().setVerticalAlignment(Element.ALIGN_MIDDLE);
            hdrTbl.calculateHeights(false);
            hdrTbl.writeSelectedRows(0, -1, MARGIN, y, cb);
            y -= HEADER_H;

            // ── 2. Badge "ĐÃ XÁC NHẬN" ───────────────────────────────────
            y -= 12f;
            float badgeW = 90f, badgeH = 18f;
            float badgeX = (PAGE_W - badgeW) / 2f;
            roundedRect(cb, badgeX, y - badgeH, badgeW, badgeH, 10f, BADGE_BG, null);

            Font bFont = new Font(vnFont, 7.5f, Font.BOLD, BADGE_FG);
            PdfPTable bTbl = oneCellTable("ĐÃ XÁC NHẬN", bFont, null,
                    badgeW, 4.5f, 0f, 0f, 0f);
            // Center the text inside the cell
            bTbl.getRow(0).getCells()[0].setHorizontalAlignment(Element.ALIGN_CENTER);
            bTbl.calculateHeights(false);
            bTbl.writeSelectedRows(0, -1, badgeX, y, cb);
            y -= badgeH + 10f;

            // ── 3. Đường kẻ ngang ─────────────────────────────────────────
            hLine(cb, MARGIN + PAD, y, INNER_W - PAD * 2, COLOR_BORDER);
            y -= 14f;

            // ── 4. Lưới thông tin (2 cột) ─────────────────────────────────
            Font lFont = new Font(vnFont, 7f,  Font.BOLD, TEXT_SUB);
            Font vFont = new Font(vnFont, 9.5f, Font.BOLD, TEXT_MAIN);

            PdfPTable info = new PdfPTable(2);
            info.setTotalWidth(INNER_W - PAD * 2);
            info.setWidths(new float[]{1f, 1f});

            info.addCell(infoCell("KHÁCH HÀNG",       nvl(msg.getUserFullName(), "—"), lFont, vFont));
            info.addCell(infoCell("MÃ ĐƠN HÀNG",      nvl(msg.getReservationId(), "—"), lFont, vFont));
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
            y -= info.getTotalHeight() + 14f;

            // ── 5. Đường cắt (dashed) với hình tròn 2 bên ────────────────
            float tearY = y + 4f;
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
            y = tearY - 16f;

            // ── 6. QR code ────────────────────────────────────────────────
            byte[] qrBytes = genQr(nvl(msg.getQrCode(), msg.getTicketId()), 200);
            Image qrImg    = Image.getInstance(qrBytes);
            float qrSz = 120f, pad = 10f;
            float boxW = qrSz + pad * 2, boxH = qrSz + pad * 2;
            float boxX = (PAGE_W - boxW) / 2f;
            float boxY = y - boxH;

            roundedRect(cb, boxX, boxY, boxW, boxH, 12f, COLOR_BLACK, null);
            qrImg.scaleAbsolute(qrSz, qrSz);
            qrImg.setAbsolutePosition(boxX + pad, boxY + pad);
            // cb.addImage thay vì doc.add() để tránh conflict với direct content
            cb.addImage(qrImg);
            y = boxY - 12f;

            // ── 7. Nhãn dưới QR ──────────────────────────────────────────
            Font scanB = new Font(vnFont, 8f,  Font.BOLD,   TEXT_SUB);
            Font scanS = new Font(vnFont, 7f,  Font.NORMAL, new Color(0x77, 0x77, 0x78));

            PdfPTable scanTbl = new PdfPTable(1);
            scanTbl.setTotalWidth(INNER_W - PAD * 2);
            scanTbl.addCell(centreCell("QUÉT VÉ KHI VÀO CỬA", scanB));
            scanTbl.addCell(centreCell("Vui lòng xuất trình vé này tại quầy check-in.", scanS));
            scanTbl.calculateHeights(false);
            scanTbl.writeSelectedRows(0, -1, MARGIN + PAD, y, cb);

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
        lb.setBorder(Rectangle.NO_BORDER); lb.setPaddingBottom(2f);
        inner.addCell(lb);
        PdfPCell vl = new PdfPCell(new Phrase(value, vFont));
        vl.setBorder(Rectangle.NO_BORDER);
        inner.addCell(vl);
        PdfPCell wrapper = new PdfPCell(inner);
        wrapper.setBorder(Rectangle.NO_BORDER);
        wrapper.setPadding(6f);
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
        if (raw == null || raw.isBlank()) return "—";
        try { return LocalDateTime.parse(raw).format(DT_FMT); }
        catch (Exception e) { return raw; }
    }

    private String seatStr(TicketEmailMessage m) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(m.getDisplayName())) sb.append(m.getDisplayName());
        if (notBlank(m.getSection()))    { if (!sb.isEmpty()) sb.append("\n"); sb.append(m.getSection()); }
        if (notBlank(m.getSeatLabel()))  { if (!sb.isEmpty()) sb.append(" ");  sb.append(m.getSeatLabel()); }
        return sb.isEmpty() ? "—" : sb.toString();
    }

    private String fmtPrice(String raw) {
        try { return VND.format(Long.parseLong(raw)) + " VND"; }
        catch (Exception e) { return nvl(raw, "—"); }
    }

    private String shortRef(String id) {
        if (id == null || id.length() < 8) return nvl(id, "—");
        return "TBX-" + id.substring(id.length() - 8).toUpperCase();
    }

    private String nvl(String s, String fb) { return (s == null || s.isBlank()) ? fb : s; }
    private boolean notBlank(String s)       { return s != null && !s.isBlank(); }
}
