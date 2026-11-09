package com.example.event.service;

import com.example.event.component.TicketEmailProducer;
import com.example.event.config.VNPayConfig;
import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.*;
import com.example.event.dto.ReservationDTO;
import com.example.event.dto.TicketSummaryDTO;
import com.example.event.entity.*;
import com.example.event.exception.AppException;
import com.example.event.repository.PaymentRepository;
import com.example.event.repository.ReservationRepository;
import com.example.event.service.Impl.VNPaymentServiceImpl;
import com.example.event.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VNPaymentServiceImplTest {

    @InjectMocks
    private VNPaymentServiceImpl paymentService;

    @Mock
    private VNPayUtil vnPayUtil;
    @Mock
    private VNPayConfig vnPayConfig;
    @Mock
    private ReservationService reservationService;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private EventInteractionService eventInteractionService;
    @Mock
    private VoucherService voucherService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private TicketEmailProducer ticketEmailProducer;
    @Mock
    private RedisService redisService;
    @Mock
    private TicketQueueService ticketQueueService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private HttpServletRequest httpRequest;

    private User sampleUser;
    private Event sampleEvent;
    private Show sampleShow;
    private Reservation sampleReservation;
    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId("user-123");
        sampleUser.setEmail("user@example.com");

        sampleEvent = new Event();
        sampleEvent.setId("event-123");
        sampleEvent.setName("Concert 2026");

        sampleShow = new Show();
        sampleShow.setId("show-123");
        sampleShow.setEvent(sampleEvent);

        sampleReservation = new Reservation();
        sampleReservation.setId("res-123");
        sampleReservation.setUser(sampleUser);
        sampleReservation.setShow(sampleShow);
        sampleReservation.setStatus(ReservationStatus.PENDING);
        sampleReservation.setTotalAmount(500000L);
        sampleReservation.setDiscountAmount(0L);
        sampleReservation.setFinalAmount(500000L);
        // Hết hạn sau 10 phút (vượt xa buffer 90s)
        sampleReservation.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        samplePayment = new Payment();
        samplePayment.setId("pay-123");
        samplePayment.setReservation(sampleReservation);
        samplePayment.setUser(sampleUser);
        samplePayment.setStatus(PaymentStatus.PENDING);
        samplePayment.setTotalAmount(500000L);
        samplePayment.setDiscountAmount(0L);
        samplePayment.setFinalAmount(500000L);
        samplePayment.setExpiresAt(LocalDateTime.now().plusMinutes(8));

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(vnPayConfig.getVnpTmnCode()).thenReturn("TMN_CODE");
        when(vnPayConfig.getSecretKey()).thenReturn("SECRET_KEY");
        when(vnPayConfig.getVnpPayUrl()).thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        when(vnPayConfig.getVnpReturnUrl()).thenReturn("http://localhost:3000/payment/callback");
        when(vnPayUtil.getIpAddress(any())).thenReturn("127.0.0.1");
        when(vnPayUtil.hmacSHA512(anyString(), anyString())).thenReturn("mockedSecureHash");
    }

    @Nested
    @DisplayName("Tests for createPaymentUrl")
    class CreatePaymentUrlTests {

        @Test
        @DisplayName("Reservation không tồn tại -> ném RESERVATION_NOT_FOUND")
        void createPaymentUrl_ReservationNotFound_ThrowsAppException() {
            // Given
            ReservationDTO dto = new ReservationDTO();
            dto.setId("res-999");
            when(reservationRepository.findReservationById("res-999")).thenReturn(null);

            // When & Then
            AppException ex = assertThrows(AppException.class, () ->
                    paymentService.createPaymentUrl(dto, httpRequest));

            assertEquals(ErrorCode.RESERVATION_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("Chưa đăng nhập -> ném UNAUTHENTICATED")
        void createPaymentUrl_Unauthenticated_ThrowsAppException() {
            // Given
            ReservationDTO dto = new ReservationDTO();
            dto.setId("res-123");
            when(reservationRepository.findReservationById("res-123")).thenReturn(sampleReservation);
            when(securityUtils.getCurrentUserId()).thenReturn(null);

            // When & Then
            AppException ex = assertThrows(AppException.class, () ->
                    paymentService.createPaymentUrl(dto, httpRequest));

            assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        }

        @Test
        @DisplayName("User không phải chủ đơn đặt chỗ -> ném ACCESS_DENIED")
        void createPaymentUrl_AccessDenied_WhenUserDoesNotMatch() {
            // Given
            ReservationDTO dto = new ReservationDTO();
            dto.setId("res-123");
            when(reservationRepository.findReservationById("res-123")).thenReturn(sampleReservation);
            when(securityUtils.getCurrentUserId()).thenReturn("other-user-999");

            // When & Then
            AppException ex = assertThrows(AppException.class, () ->
                    paymentService.createPaymentUrl(dto, httpRequest));

            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Reservation đã hết hạn (hoặc trong buffer 90s) -> ném RESERVATION_EXPIRED")
        void createPaymentUrl_ReservationExpired_ThrowsAppException() {
            // Given
            ReservationDTO dto = new ReservationDTO();
            dto.setId("res-123");
            // Chỉ còn 30 giây (nhỏ hơn buffer 90s)
            sampleReservation.setExpiresAt(LocalDateTime.now().plusSeconds(30));
            when(reservationRepository.findReservationById("res-123")).thenReturn(sampleReservation);

            // When & Then
            AppException ex = assertThrows(AppException.class, () ->
                    paymentService.createPaymentUrl(dto, httpRequest));

            assertEquals(ErrorCode.RESERVATION_EXPIRED, ex.getErrorCode());
        }

        @Test
        @DisplayName("Không lấy được Distributed Lock (request trùng lặp) -> ném DUPLICATE_PAYMENT_REQUEST_IN_PROGRESS")
        void createPaymentUrl_LockAcquisitionFailed_ThrowsAppException() {
            // Given
            ReservationDTO dto = new ReservationDTO();
            dto.setId("res-123");
            when(reservationRepository.findReservationById("res-123")).thenReturn(sampleReservation);
            when(redisService.setIfAbsent(startsWith("PAYMENT_LOCK:"), anyString(), anyLong())).thenReturn(false);

            // When & Then
            AppException ex = assertThrows(AppException.class, () ->
                    paymentService.createPaymentUrl(dto, httpRequest));

            assertEquals(ErrorCode.DUPLICATE_PAYMENT_REQUEST_IN_PROGRESS, ex.getErrorCode());
        }

        @Test
        @DisplayName("Idempotent hit cache: Có sẵn URL thanh toán trong Redis -> Trả về URL ngay")
        void createPaymentUrl_CacheHit_ReturnsCachedUrl() {
            // Given
            ReservationDTO dto = new ReservationDTO();
            dto.setId("res-123");
            dto.setVoucherId(null);
            sampleReservation.setVoucher(null);

            when(reservationRepository.findReservationById("res-123")).thenReturn(sampleReservation);
            when(redisService.setIfAbsent(startsWith("PAYMENT_LOCK:"), anyString(), anyLong())).thenReturn(true);
            when(redisService.get(eq("PAYMENT_LOCK:res-123"), eq(String.class))).thenAnswer(inv -> "sample-lock-token");
            when(redisService.get(startsWith("PAYMENT_URL:"), eq(String.class)))
                    .thenReturn("https://sandbox.vnpayment.vn/cached-payment-url");

            // When
            String url = paymentService.createPaymentUrl(dto, httpRequest);

            // Then
            assertEquals("https://sandbox.vnpayment.vn/cached-payment-url", url);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tạo payment URL mới thành công khi chưa áp dụng voucher")
        void createPaymentUrl_Success_WithoutVoucher_CreatesNewPayment() {
            // Given
            ReservationDTO dto = new ReservationDTO();
            dto.setId("res-123");
            dto.setVoucherId(null);
            sampleReservation.setVoucher(null);
            sampleReservation.setPayment(null);

            when(reservationRepository.findReservationById("res-123")).thenReturn(sampleReservation);
            when(redisService.setIfAbsent(startsWith("PAYMENT_LOCK:"), anyString(), anyLong())).thenReturn(true);
            when(redisService.get(startsWith("PAYMENT_URL:"), eq(String.class))).thenReturn(null);

            // When
            String url = paymentService.createPaymentUrl(dto, httpRequest);

            // Then
            assertNotNull(url);
            assertTrue(url.contains("vnp_Amount=50000000")); // 500k * 100
            assertTrue(url.contains("vnp_SecureHash=mockedSecureHash"));
            verify(paymentRepository, times(1)).save(any(Payment.class));
            verify(redisService).set(startsWith("PAYMENT_URL:res-123:NONE"), eq(url), anyLong());
        }

        @Test
        @DisplayName("Áp dụng voucher mới -> validate, reserve voucher, tính discount và tạo URL")
        void createPaymentUrl_Success_WithNewVoucher() {
            // Given
            ReservationDTO dto = new ReservationDTO();
            dto.setId("res-123");
            dto.setVoucherId("voucher-promo");
            sampleReservation.setVoucher(null);

            Voucher newVoucher = new Voucher();
            newVoucher.setId("voucher-promo");

            ReservationDTO calculatedDTO = new ReservationDTO();
            calculatedDTO.setTotalAmount(500000L);
            calculatedDTO.setDiscountAmount(50000L);
            calculatedDTO.setFinalAmount(450000L);

            when(reservationRepository.findReservationById("res-123")).thenReturn(sampleReservation);
            when(redisService.setIfAbsent(startsWith("PAYMENT_LOCK:"), anyString(), anyLong())).thenReturn(true);
            when(voucherService.validateVoucherForReservation("voucher-promo", sampleReservation)).thenReturn(newVoucher);
            when(reservationService.calculateDiscount(sampleReservation, newVoucher)).thenReturn(calculatedDTO);

            // Khi applyDiscount được gọi, cập nhật finalAmount trên sampleReservation
            doAnswer(invocation -> {
                sampleReservation.setFinalAmount(450000L);
                sampleReservation.setDiscountAmount(50000L);
                return null;
            }).when(reservationService).applyDiscount(eq(sampleReservation), eq(newVoucher), any());

            // When
            String url = paymentService.createPaymentUrl(dto, httpRequest);

            // Then
            assertNotNull(url);
            verify(voucherService).validateVoucherForReservation("voucher-promo", sampleReservation);
            verify(voucherService).reserveVoucher("voucher-promo");
            verify(reservationService).applyDiscount(eq(sampleReservation), eq(newVoucher), eq(calculatedDTO));
            verify(paymentRepository, times(1)).save(any(Payment.class));
        }

        @Test
        @DisplayName("Lỗi khi áp dụng voucher mới -> tự động rollback release voucher")
        void createPaymentUrl_VoucherChangeFails_RollsBackVoucher() {
            // Given
            ReservationDTO dto = new ReservationDTO();
            dto.setId("res-123");
            dto.setVoucherId("voucher-promo");
            sampleReservation.setVoucher(null);

            Voucher newVoucher = new Voucher();
            newVoucher.setId("voucher-promo");

            when(reservationRepository.findReservationById("res-123")).thenReturn(sampleReservation);
            when(redisService.setIfAbsent(startsWith("PAYMENT_LOCK:"), anyString(), anyLong())).thenReturn(true);
            when(voucherService.validateVoucherForReservation("voucher-promo", sampleReservation)).thenReturn(newVoucher);
            doNothing().when(voucherService).reserveVoucher("voucher-promo");
            when(reservationService.calculateDiscount(any(), any()))
                    .thenThrow(new RuntimeException("Calculation database error"));

            // When & Then
            assertThrows(RuntimeException.class, () ->
                    paymentService.createPaymentUrl(dto, httpRequest));

            // Phải gọi rollback release voucher
            verify(voucherService).release("voucher-promo");
        }

        @Test
        @DisplayName("Hủy bỏ voucher đang dùng -> release voucher cũ và resetDiscount")
        void createPaymentUrl_RemoveExistingVoucher_ReleasesOldVoucher() {
            // Given
            ReservationDTO dto = new ReservationDTO();
            dto.setId("res-123");
            dto.setVoucherId(null);

            Voucher oldVoucher = new Voucher();
            oldVoucher.setId("old-voucher-123");
            sampleReservation.setVoucher(oldVoucher);

            when(reservationRepository.findReservationById("res-123")).thenReturn(sampleReservation);
            when(redisService.setIfAbsent(startsWith("PAYMENT_LOCK:"), anyString(), anyLong())).thenReturn(true);

            // When
            paymentService.createPaymentUrl(dto, httpRequest);

            // Then
            verify(voucherService).release("old-voucher-123");
            verify(reservationService).resetDiscount(sampleReservation);
        }
    }

    @Nested
    @DisplayName("Tests for processReturn")
    class ProcessReturnTests {

        @Test
        @DisplayName("Chữ ký hợp lệ -> trả về status OK kèm thông tin giao dịch")
        void processReturn_ValidSignature_ReturnsStatusOK() {
            // Given
            when(httpRequest.getParameterNames()).thenReturn(Collections.enumeration(List.of(
                    "vnp_Amount", "vnp_BankCode", "vnp_ResponseCode", "vnp_SecureHash", "vnp_TxnRef"
            )));
            when(httpRequest.getParameter("vnp_Amount")).thenReturn("50000000");
            when(httpRequest.getParameter("vnp_BankCode")).thenReturn("NCB");
            when(httpRequest.getParameter("vnp_ResponseCode")).thenReturn("00");
            when(httpRequest.getParameter("vnp_TxnRef")).thenReturn("pay-123_1678900000");
            when(httpRequest.getParameter("vnp_SecureHash")).thenReturn("VALID_HASH");

            when(vnPayUtil.hmacSHA512(eq("SECRET_KEY"), anyString())).thenReturn("VALID_HASH");

            // When
            Map<String, String> result = paymentService.processReturn(httpRequest);

            // Then
            assertEquals("OK", result.get("status"));
            assertEquals("Thanh toán thành công", result.get("message"));
            assertEquals("pay-123_1678900000", result.get("txnRef"));
            assertEquals("00", result.get("responseCode"));
        }

        @Test
        @DisplayName("Chữ ký không khớp -> trả về status FAILED")
        void processReturn_InvalidSignature_ReturnsStatusFAILED() {
            // Given
            when(httpRequest.getParameterNames()).thenReturn(Collections.enumeration(List.of(
                    "vnp_Amount", "vnp_SecureHash"
            )));
            when(httpRequest.getParameter("vnp_Amount")).thenReturn("50000000");
            when(httpRequest.getParameter("vnp_SecureHash")).thenReturn("FORGED_HASH");

            when(vnPayUtil.hmacSHA512(eq("SECRET_KEY"), anyString())).thenReturn("REAL_HASH");

            // When
            Map<String, String> result = paymentService.processReturn(httpRequest);

            // Then
            assertEquals("FAILED", result.get("status"));
            assertEquals("Chữ ký không hợp lệ", result.get("message"));
        }
    }

    @Nested
    @DisplayName("Tests for processPayment")
    class ProcessPaymentTests {

        @Test
        @DisplayName("Kết quả callback có status != OK -> ném PAYMENT_NOT_AVAILABLE")
        void processPayment_InvalidStatus_ThrowsAppException() {
            // Given
            Map<String, String> result = Map.of(
                    "status", "FAILED",
                    "message", "Chữ ký không hợp lệ"
            );

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> paymentService.processPayment(result));
            assertEquals(ErrorCode.PAYMENT_NOT_AVAILABLE, ex.getErrorCode());
        }

        @Test
        @DisplayName("Không tìm thấy Payment theo ID trong txnRef -> ném PAYMENT_NOT_FOUND")
        void processPayment_PaymentNotFound_ThrowsAppException() {
            // Given
            Map<String, String> result = Map.of(
                    "status", "OK",
                    "txnRef", "pay-999_12345678",
                    "responseCode", "00"
            );
            when(paymentRepository.findPaymentByIdForUpdate("pay-999")).thenReturn(null);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> paymentService.processPayment(result));
            assertEquals(ErrorCode.PAYMENT_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("Payment không ở trạng thái PENDING (đã xử lý trước đó) -> ném PAYMENT_NOT_AVAILABLE")
        void processPayment_PaymentNotPending_ThrowsAppException() {
            // Given
            samplePayment.setStatus(PaymentStatus.SUCCESS);
            Map<String, String> result = Map.of(
                    "status", "OK",
                    "txnRef", "pay-123_12345678",
                    "amount", "50000000",
                    "responseCode", "00"
            );
            when(paymentRepository.findPaymentByIdForUpdate("pay-123")).thenReturn(samplePayment);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> paymentService.processPayment(result));
            assertEquals(ErrorCode.PAYMENT_NOT_AVAILABLE, ex.getErrorCode());
        }

        @Test
        @DisplayName("Số tiền từ VNPay không khớp với finalAmount trong DB -> ném INVALID_AMOUNT")
        void processPayment_InvalidAmount_ThrowsAppException() {
            // Given
            samplePayment.setFinalAmount(500000L);
            Map<String, String> result = Map.of(
                    "status", "OK",
                    "txnRef", "pay-123_12345678",
                    "amount", "10000000", // 100k thay vì 500k
                    "responseCode", "00"
            );
            when(paymentRepository.findPaymentByIdForUpdate("pay-123")).thenReturn(samplePayment);

            // When & Then
            AppException ex = assertThrows(AppException.class, () -> paymentService.processPayment(result));
            assertEquals(ErrorCode.INVALID_AMOUNT, ex.getErrorCode());
        }

        @Test
        @DisplayName("Thanh toán thành công (00) -> cập nhật SUCCESS, PAID, xuất vé, gửi mail, rời queue và WebSocket")
        void processPayment_Success_00_CompleteWorkflow() {
            // Given
            samplePayment.setFinalAmount(500000L);
            Map<String, String> result = Map.of(
                    "status", "OK",
                    "txnRef", "pay-123_12345678",
                    "amount", "50000000",
                    "responseCode", "00",
                    "transactionNo", "VNPAY123456",
                    "bankCode", "NCB",
                    "payDate", "20261108200000"
            );
            when(paymentRepository.findPaymentByIdForUpdate("pay-123")).thenReturn(samplePayment);

            List<TicketSummaryDTO> mockTickets = List.of(new TicketSummaryDTO());
            when(ticketService.generateTickets("res-123")).thenReturn(mockTickets);

            // When
            paymentService.processPayment(result);

            // Then
            // 1. Kiểm tra trạng thái Payment
            assertEquals(PaymentStatus.SUCCESS, samplePayment.getStatus());
            assertEquals("VNPAY123456", samplePayment.getTransactionNo());
            assertEquals("NCB", samplePayment.getBankCode());
            verify(paymentRepository).save(samplePayment);

            // 2. Kiểm tra trạng thái Reservation
            assertEquals(ReservationStatus.PAID, sampleReservation.getStatus());
            verify(reservationRepository).save(sampleReservation);

            // 3. Kiểm tra tương tác, xuất vé, gửi mail và rời hàng đợi
            verify(eventInteractionService).addInteraction("event-123", "user-123", InteractionType.REGISTER);
            verify(ticketService).generateTickets("res-123");
            verify(ticketEmailProducer).sendReservationTickets(sampleUser, mockTickets);
            verify(ticketQueueService).leaveQueue("show-123", "user-123");

            // 4. Kiểm tra thông báo WebSocket
            ArgumentCaptor<Map<String, String>> wsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/reservations/res-123/payment-response"), wsCaptor.capture());
            assertEquals("SUCCESS", wsCaptor.getValue().get("status"));
        }

        @Test
        @DisplayName("VNPay báo thành công (00) nhưng Reservation đã EXPIRED/CANCELLED -> Chuyển Payment sang REFUND_PENDING, không xuất vé")
        void processPayment_Success_00_WhenReservationExpiredOrCancelled_SetsRefundPending() {
            // Given
            sampleReservation.setStatus(ReservationStatus.EXPIRED);
            samplePayment.setFinalAmount(500000L);
            Map<String, String> result = Map.of(
                    "status", "OK",
                    "txnRef", "pay-123_12345678",
                    "amount", "50000000",
                    "responseCode", "00",
                    "transactionNo", "VNPAY123456",
                    "bankCode", "NCB"
            );
            when(paymentRepository.findPaymentByIdForUpdate("pay-123")).thenReturn(samplePayment);

            // When
            paymentService.processPayment(result);

            // Then
            // Chuyển sang hoàn tiền
            assertEquals(PaymentStatus.REFUND_PENDING, samplePayment.getStatus());
            verify(paymentRepository).save(samplePayment);

            // KHÔNG được xuất vé hay gửi email
            verify(ticketService, never()).generateTickets(anyString());
            verify(ticketEmailProducer, never()).sendReservationTickets(any(), any());

            // WebSocket báo lỗi kèm ghi nhận hoàn tiền
            ArgumentCaptor<Map<String, String>> wsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/reservations/res-123/payment-response"), wsCaptor.capture());
            assertEquals("FAILED", wsCaptor.getValue().get("status"));
            assertTrue(wsCaptor.getValue().get("message").contains("hoàn tiền"));
        }

        @Test
        @DisplayName("Khách hủy giao dịch (code 24) -> Payment FAILED, bắn WebSocket thông báo lỗi")
        void processPayment_Failed_Code24_CustomerCancelled() {
            // Given
            samplePayment.setFinalAmount(500000L);
            Map<String, String> result = Map.of(
                    "status", "OK",
                    "txnRef", "pay-123_12345678",
                    "amount", "50000000",
                    "responseCode", "24",
                    "transactionNo", "VNPAY_FAIL_24"
            );
            when(paymentRepository.findPaymentByIdForUpdate("pay-123")).thenReturn(samplePayment);

            // When
            paymentService.processPayment(result);

            // Then
            assertEquals(PaymentStatus.FAILED, samplePayment.getStatus());
            verify(paymentRepository).save(samplePayment);
            verify(ticketService, never()).generateTickets(anyString());

            ArgumentCaptor<Map<String, String>> wsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/reservations/res-123/payment-response"), wsCaptor.capture());
            assertEquals("FAILED", wsCaptor.getValue().get("status"));
            assertEquals("Khách hàng hủy giao dịch", wsCaptor.getValue().get("message"));
        }
    }
}
