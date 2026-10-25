package com.example.event.controller;

import com.example.event.dto.ReservationDTO;
import com.example.event.dto.response.ApiResponse;
import com.example.event.service.PaymentService;
import com.example.event.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final TicketService ticketService;

    @PostMapping("/create_payment_url")
    public ResponseEntity<?> createPayment(@RequestBody ReservationDTO request, HttpServletRequest httpRequest, @RequestHeader(name = "Idempotency-Key") String idempotencyKey) {
        String paymentUrl = paymentService.createPaymentUrl(request, httpRequest, idempotencyKey);
        ApiResponse response = ApiResponse.builder()
                .message("Thành công")
                .status(HttpStatus.OK.value())
                .data(paymentUrl)
                .build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping(value = "/vnpay-callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleVNPayCallback(HttpServletRequest request) {
        try {
            Map<String, String> result = paymentService.processReturn(request);
            paymentService.processPayment(result);
        } catch (Exception e) {
            // Đã được xử lý hoặc có lỗi, vẫn trả về giao diện đóng tab để không làm gián đoạn người dùng
        }

        String html = """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Thanh toán hoàn tất</title>
                <style>
                    body {
                        margin: 0;
                        background-color: #0e0e0e;
                        color: #e7e5e5;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 100vh;
                        text-align: center;
                        padding: 16px;
                    }
                    .card {
                        background: #181818;
                        border: 1px solid #333333;
                        border-radius: 16px;
                        padding: 32px 24px;
                        max-width: 380px;
                        width: 100%;
                        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
                    }
                    .icon {
                        width: 48px;
                        height: 48px;
                        border-radius: 50%;
                        background: rgba(34, 197, 94, 0.15);
                        color: #22c55e;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 24px;
                        margin: 0 auto 16px;
                        border: 1px solid rgba(34, 197, 94, 0.3);
                    }
                    h2 {
                        font-size: 18px;
                        margin: 0 0 8px;
                        color: #ffffff;
                    }
                    p {
                        color: #a1a1aa;
                        font-size: 14px;
                        margin: 0 0 20px;
                        line-height: 1.5;
                    }
                    button {
                        background: #22c55e;
                        color: #0b0b0b;
                        border: none;
                        padding: 10px 24px;
                        border-radius: 8px;
                        font-size: 14px;
                        font-weight: 600;
                        cursor: pointer;
                        transition: background-color 0.2s;
                    }
                    button:hover {
                        background: #16a34a;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">✓</div>
                    <h2>Giao dịch hoàn tất</h2>
                    <p>Tab này sẽ tự động đóng sau giây lát. Bạn có thể quay lại trang đặt vé.</p>
                    <button onclick="window.close()">Đóng cửa sổ này</button>
                </div>
                <script>
                    window.close();
                    setTimeout(function() {
                        window.close();
                    }, 500);
                </script>
            </body>
            </html>
            """;

        return ResponseEntity.ok(html);
    }
}
