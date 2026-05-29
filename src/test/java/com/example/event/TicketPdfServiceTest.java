package com.example.event;

import com.example.event.dto.TicketEmailMessage;
import com.example.event.service.TicketPdfService;
import org.junit.jupiter.api.Test;
import java.io.FileOutputStream;

class TicketPdfServiceTest {

    @Test
    void testGeneratePdf() throws Exception {
        TicketPdfService service = new TicketPdfService();

        TicketEmailMessage msg = new TicketEmailMessage();
        msg.setTicketId("TK-998877");
        msg.setEventName("Jazz & Symphony Night");
        msg.setEventLocation("Nhà Hát Lớn Hà Nội, Tràng Tiền, Hoàn Kiếm");
        msg.setUserFullName("NGUYỄN HOÀNG LONG");
        msg.setReservationId("RES-55443322");
        msg.setShowStartTime("2026-05-30T19:30:00");
        msg.setDisplayName("VÉ VIP");
        msg.setSection("KHU A");
        msg.setSeatLabel("HÀNG C - GHẾ 12");
        msg.setUnitPrice("1500000");
        msg.setQrCode("https://eventhunting.example.com/checkin/TK-998877");

        byte[] pdfBytes = service.generatePdf(msg);

        try (FileOutputStream fos = new FileOutputStream("../ticket_preview_v5.pdf")) {
            fos.write(pdfBytes);
        }
        System.out.println(">>> Đã tạo file xem trước vé thành công tại: ../ticket_preview_v5.pdf");
    }
}
