package com.example.event.service.Impl;

import com.example.event.component.TicketEmailProducer;
import com.example.event.config.VNPayConfig;
import com.example.event.constant.*;
import com.example.event.dto.ReservationDTO;
import com.example.event.dto.TicketSummaryDTO;
import com.example.event.entity.*;
import com.example.event.exception.AppException;
import com.example.event.repository.PaymentRepository;
import com.example.event.repository.ReservationRepository;
import com.example.event.service.*;
import com.example.event.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class VNPaymentServiceImpl implements PaymentService {
    private final VNPayUtil vnPayUtil;
    private final VNPayConfig vnPayConfig;
    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final EventInteractionService eventInteractionService;
    private final VoucherService voucherService;
    private final PaymentRepository paymentRepository;
    private final TicketService ticketService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TicketEmailProducer ticketEmailProducer;
    private final RedisService redisService;
    private static final Map<String, String> VNPAY_MESSAGES;
    private static final String PAYMENT_IDEMPOTENCY_KEY_FORMAT = "PAYMENT_CREATE:%s";
    private static final long LOCK_TIMEOUT_SECONDS = 10; // 10s

    static {
        Map<String, String> tempMap = new HashMap<>();
        tempMap.put("00", "Giao dịch thành công");
        tempMap.put("01", "Giao dịch chưa hoàn tất");
        tempMap.put("02", "Giao dịch bị lỗi");
        tempMap.put("04", "Giao dịch đảo (Đã trừ tiền nhưng chưa thành công tại VNPAY)");
        tempMap.put("05", "VNPAY đang xử lý hoàn tiền");
        tempMap.put("06", "VNPAY đã gửi yêu cầu hoàn tiền sang Ngân hàng");
        tempMap.put("07", "Giao dịch bị nghi ngờ gian lận");
        tempMap.put("09", "Giao dịch hoàn trả bị từ chối");
        VNPAY_MESSAGES = Collections.unmodifiableMap(tempMap);
    }

    /**
     * Tạo URL thanh toán VNPay kèm cơ chế Idempotency chống trùng lặp.
     * Sử dụng Redis Distributed Lock + TTL động theo thời gian hết hạn của đơn giữ chỗ (Reservation).
     */
    @Override
    @Transactional
    public String createPaymentUrl(ReservationDTO reservationDTO, HttpServletRequest httpRequest, String idempotencyKey) {
        String reservationId = reservationDTO.getId();
        LocalDateTime now = LocalDateTime.now();
        String paymentIdempotencyKey = String.format(
                PAYMENT_IDEMPOTENCY_KEY_FORMAT,
                idempotencyKey
        );

        Reservation reservation = Optional.ofNullable(reservationRepository.findReservationById(reservationId))
                .orElseThrow(() -> {
                    log.warn("[PAYMENT] User {} | Reservation {} không tìm thấy được đơn đặt.", reservationDTO.getUserId(), reservationId);
                    return new AppException(ErrorCode.RESERVATION_NOT_FOUND);
                });

        // validate dữ liệu reservation;
        reservationService.validateReservationForPayment(reservation);

        // Tính thời gian còn lại của reservation
        long remainingSeconds = java.time.Duration.between(now, reservation.getExpiresAt()).getSeconds();
        if (remainingSeconds <= 0) {
            log.warn("[PAYMENT] Reservation {} đã hết hạn | expiresAt={}", reservationId, reservation.getExpiresAt());
            throw new AppException(ErrorCode.RESERVATION_EXPIRED);
        }

        // VNPay URL thường có hạn tối đa 15 phút, lấy min giữa thời gian giữ vé còn lại và 15 phút
        long effectiveTtl = Math.min(remainingSeconds, 15 * 60L);

        // Kiểm tra cache idempotency
        boolean acquired = redisService.setIfAbsent(paymentIdempotencyKey, "PROCESSING", LOCK_TIMEOUT_SECONDS);
        if (!acquired) {
            String cachedPaymentUrl = redisService.get(paymentIdempotencyKey, String.class);
            if ("PROCESSING".equals(cachedPaymentUrl)) {
                log.warn("[PAYMENT] Request trùng lặp đang được xử lý | ReservationId={}", reservationId);
                throw new AppException(ErrorCode.DUPLICATE_PAYMENT_REQUEST_IN_PROGRESS);
            }
            if (cachedPaymentUrl != null) {
                log.info("[PAYMENT] Idempotent request đã thấy | ReservationId={} | trả về url trong cache",
                        reservationId);
                return cachedPaymentUrl;
            }
            // Key vừa bị giải phóng hoặc vừa hết hạn trong tích tắc: chặn để client thử lại an toàn
            throw new AppException(ErrorCode.DUPLICATE_PAYMENT_REQUEST_IN_PROGRESS);
        }
        try {
            // Xử lý áp dụng/thay đổi voucher
            handleVoucherChange(reservation, reservationDTO);
            // Tạo hoặc cập nhật payment
            Payment payment = Optional.ofNullable(findPaymentAndUpdate(reservation))
                    .orElseGet(() -> createNewPayment(reservation, now));
            // Tạo VNPay URL
            String paymentUrl = buildPaymentUrl(payment, httpRequest);
            // Lưu cache với thời gian sống theo reservation
            redisService.set(paymentIdempotencyKey, paymentUrl, effectiveTtl);
            return paymentUrl;
        } catch (Exception e) {
            redisService.del(paymentIdempotencyKey);
            throw e;
        }
    }

    /**
     * Tiếp nhận dữ liệu trả về từ VNPay (Return URL / IPN), xác thực chữ ký (Checksum) và chuẩn hóa dữ liệu.
     */
    @Override
    public Map<String, String> processReturn(HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements(); ) {
            String fieldName = params.nextElement();
            String fieldValue = request.getParameter(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                fields.put(fieldName, fieldValue);
            }
        }

        String vnp_SecureHash = request.getParameter("vnp_SecureHash");
        fields.remove("vnp_SecureHash");
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            try {
                String fieldName = itr.next();
                String fieldValue = fields.get(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    hashData.append(fieldName).append("=").append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    if (itr.hasNext()) {
                        hashData.append("&");
                    }
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        // Kiểm tra chữ ký bảo mật với secret key
        String signValue = vnPayUtil.hmacSHA512(vnPayConfig.getSecretKey(), hashData.toString());
        Map<String, String> result = new HashMap<>();
        if (signValue.equals(vnp_SecureHash)) {
            result.put("status", "OK");
            result.put("message", "Thanh toán thành công");
            result.put("txnRef", fields.get("vnp_TxnRef"));
            result.put("transactionNo", fields.get("vnp_TransactionNo"));
            result.put("bankCode", fields.get("vnp_BankCode"));
            result.put("amount", fields.get("vnp_Amount"));
            result.put("payDate", fields.get("vnp_PayDate"));
            result.put("orderInfo", fields.get("vnp_OrderInfo"));
            result.put("responseCode", fields.get("vnp_ResponseCode"));
        } else {
            result.put("status", "FAILED");
            result.put("message", "Chữ ký không hợp lệ");
        }
        return result;
    }

    /**
     * Xử lý kết quả thanh toán: Khóa dòng pessimistic lock, cập nhật trạng thái đơn hàng và kích hoạt nghiệp vụ sau thanh toán.
     */
    @Override
    @Transactional
    public void processPayment(Map<String, String> result) {
        
        String txnRef = result.get("txnRef");
        String responseCode = result.get("responseCode");
        String paymentId = extractIdFromTxnRef(txnRef);

        // Khóa bi quan (Pessimistic Lock) để chống race condition khi callback bị gọi lặp lại
        Payment payment = Optional.ofNullable(paymentRepository.findPaymentByIdForUpdate(paymentId))
                .orElseThrow(() -> {
                    log.warn("[PAYMENT] Không tìm thấy Payment với ID: {}", paymentId);
                    return new AppException(ErrorCode.PAYMENT_NOT_FOUND);
                });

        validatePaymentState(payment, result);
        updatePaymentDetails(payment, result, responseCode);
        handlePostPaymentBusiness(payment, responseCode);
    }

    /**
     * Kiểm tra trạng thái và số tiền của Payment để tránh thanh toán lặp hoặc sai lệch số tiền.
     */
    private void validatePaymentState(Payment payment, Map<String, String> result) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("[PAYMENT] Giao dịch {} đã được xử lý trước đó (Status: {}). Bỏ qua callback.",
                    payment.getId(), payment.getStatus());
            throw new AppException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }

        long vnpAmount = Long.parseLong(result.get("amount")) / 100;
        if (!payment.getFinalAmount().equals(vnpAmount)) {
            log.warn("[PAYMENT] Sai lệch số tiền thanh toán. DB: {}, VNPAY: {}",
                    payment.getFinalAmount(), vnpAmount);
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }
    }

    /**
     * Cập nhật thông tin chi tiết giao dịch từ VNPay vào bản ghi Payment.
     */
    private void updatePaymentDetails(Payment payment, Map<String, String> result, String responseCode) {
        ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime now = LocalDateTime.now(vnZone);

        payment.setStatus("00".equals(responseCode) ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        payment.setTransactionNo(result.get("transactionNo"));
        payment.setBankCode(result.get("bankCode"));
        payment.setMethod(PaymentMethod.VNPAY);
        payment.setResponseCode(responseCode);
        payment.setUpdatedAt(now);
        payment.setUpdatedBy(payment.getUser().getId());

        String payDateStr = result.get("payDate");
        if (payDateStr != null && !payDateStr.isEmpty()) {
            try {
                payment.setPaidAt(LocalDateTime.parse(payDateStr, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            } catch (Exception e) {
                log.error("Lỗi parse ngày thanh toán: {}", payDateStr);
            }
        }
        
        paymentRepository.save(payment);
    }

    /**
     * Xử lý các logic nghiệp vụ sau khi có kết quả thanh toán: cập nhật đơn đặt chỗ, phát vé, gửi email, bắn WebSocket.
     */
    private void handlePostPaymentBusiness(Payment payment, String responseCode) {
        Reservation reservation = payment.getReservation();
        User user = reservation.getUser();
        Show show = reservation.getShow();
        Event event = show.getEvent();
        if (user != null && event != null) eventInteractionService.addInteraction(event.getId(), user.getId(), InteractionType.REGISTER);
        String resId = reservation.getId();
        Map<String, String> socketData = new HashMap<>();
        socketData.put("reservationId", resId);

        if ("00".equals(responseCode)) {
            reservation.setStatus(ReservationStatus.PAID);
            reservation.setUpdatedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
            reservationRepository.save(reservation);

            // Tạo vé và gửi toàn bộ theo đợt (batch) vào Redis stream để xử lý gửi email
            List<TicketSummaryDTO> tickets = ticketService.generateTickets(resId);
            ticketEmailProducer.sendReservationTickets(user, tickets);

            socketData.put("status", "SUCCESS");
            socketData.put("message", "Thanh toán thành công");
        } else {
            socketData.put("status", "FAILED");
            socketData.put("message", VNPAY_MESSAGES.getOrDefault(responseCode, "Giao dịch thất bại"));
        }

        messagingTemplate.convertAndSend("/topic/reservations/" + resId + "/payment-response", socketData);
    }

    /**
     * Tách ID giao dịch gốc từ chuỗi txnRef (được ghép theo dạng {paymentId}_{timestamp}).
     */
    private String extractIdFromTxnRef(String txnRef) {
        return (txnRef != null && txnRef.contains("_")) ? txnRef.split("_")[0] : txnRef;
    }

    /**
     * Kiểm tra tính hợp lệ của đơn đặt chỗ (thời hạn, số tiền).
     */
    private void validateReservation(Reservation res, ReservationDTO dto) {
        if (LocalDateTime.now().isAfter(res.getExpiresAt()))
            throw new AppException(ErrorCode.RESERVATION_EXPIRED);
        if (!res.getTotalAmount().equals(dto.getTotalAmount()))
            throw new AppException(ErrorCode.INVALID_AMOUNT);
    }

    /**
     * Tạo mới một bản ghi Payment gắn với đơn đặt chỗ ở trạng thái PENDING.
     */
    private Payment createNewPayment(Reservation reservation, LocalDateTime now) {
        User user = reservation.getUser();
        Payment newPayment = new Payment();
        newPayment.setTotalAmount(reservation.getTotalAmount());
        newPayment.setDiscountAmount(reservation.getDiscountAmount());
        newPayment.setFinalAmount(reservation.getFinalAmount());
        newPayment.setStatus(PaymentStatus.PENDING);
        newPayment.setExpiresAt(reservation.getExpiresAt());
        newPayment.setReservation(reservation);
        newPayment.setUser(reservation.getUser());

        newPayment.setCreatedAt(now);
        newPayment.setCreatedBy(user.getId());
        newPayment.setUpdatedAt(now);
        newPayment.setUpdatedBy(user.getId());
        paymentRepository.save(newPayment);
        log.info("[PAYMENT] User {} | Payment {} đã được tạo", user.getId(), newPayment.getId());
        return newPayment;
    }

    /**
     * Xây dựng URL thanh toán VNPay kèm các tham số cấu hình, thời gian hết hạn và chữ ký số SHA-512.
     */
    private String buildPaymentUrl(Payment payment, HttpServletRequest httpRequest) {
        String version = "2.1.0";
        String command = "pay";
        String orderType = "other";
        // Số tiền theo quy định của VNPay cần nhân với 100
        long amount = payment.getFinalAmount() * 100;
        String transactionReference = payment.getId() + "_" + System.currentTimeMillis();
        String clientIpAddress = vnPayUtil.getIpAddress(httpRequest);
        String terminalCode = vnPayConfig.getVnpTmnCode();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", version);
        params.put("vnp_Command", command);
        params.put("vnp_TmnCode", terminalCode);
        params.put("vnp_Amount", String.valueOf(amount));
        params.put("vnp_CurrCode", "VND");

        params.put("vnp_TxnRef", transactionReference);
        params.put("vnp_OrderInfo", "Thanh toan don hang: " + transactionReference);
        params.put("vnp_OrderType", orderType);

        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", vnPayConfig.getVnpReturnUrl());
        params.put("vnp_IpAddr", clientIpAddress);

        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        ZonedDateTime now = ZonedDateTime.now(zone);

        params.put("vnp_CreateDate", now.format(formatter));

        ZonedDateTime expire = payment.getExpiresAt().atZone(zone);
        params.put("vnp_ExpireDate", expire.format(formatter));

        // Sắp xếp tham số theo alphabet và tạo Query String
        List<String> sortedFieldNames = new ArrayList<>(params.keySet());
        Collections.sort(sortedFieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = sortedFieldNames.iterator();

        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = params.get(fieldName);
            try {
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    if (itr.hasNext()) {
                        query.append('&');
                        hashData.append('&');
                    }
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        String queryUrl = query.toString();
        String secureHash = vnPayUtil.hmacSHA512(vnPayConfig.getSecretKey(), hashData.toString());
        queryUrl += "&vnp_SecureHash=" + secureHash;
        return vnPayConfig.getVnpPayUrl() + "?" + queryUrl;
    }

    /**
     * Tìm Payment đã tồn tại của đơn đặt chỗ để cập nhật lại số tiền nếu có thay đổi.
     */
    private Payment findPaymentAndUpdate(Reservation reservation) {
        Payment payment = reservation.getPayment();
        if (payment == null) return null;
        payment.setTotalAmount(reservation.getTotalAmount());
        payment.setDiscountAmount(reservation.getDiscountAmount());
        payment.setFinalAmount(reservation.getFinalAmount());
        paymentRepository.save(payment);
        return payment;
    }

    /**
     * Xử lý logic thay đổi voucher: kiểm tra tính hợp lệ, giữ số lượng voucher, tính lại chiết khấu và hoàn tác nếu lỗi.
     */
    private void handleVoucherChange(Reservation reservation, ReservationDTO dto) {
        Voucher currentVoucher = reservation.getVoucher();
        String newVoucherId = dto.getVoucherId();

        // Trường hợp 1: Không có voucher cũ lẫn mới, không làm gì
        if (newVoucherId == null && currentVoucher == null) return;

        // Trường hợp 2: Bỏ voucher đang áp dụng
        if (newVoucherId == null) {
            log.info("[VOUCHER] Reservation {} | Bỏ voucher {}", reservation.getId(), currentVoucher.getId());
            voucherService.release(currentVoucher.getId());
            reservationService.resetDiscount(reservation);
            return;
        }

        // Trường hợp 3: Áp dụng voucher mới
        Voucher newVoucher = null;
        boolean isReservedIncreased = false;

        try {
            boolean isSameVoucher = currentVoucher != null
                    && currentVoucher.getId().equals(newVoucherId);

            if (isSameVoucher) {
                // Trường hợp 3a: Cùng voucher → chỉ tính lại chiết khấu, không thay đổi số lượng giữ chỗ (reservedQty)
                newVoucher = currentVoucher;
                log.info("[VOUCHER] Reservation {} | Giữ nguyên voucher {}, recalculate",
                        reservation.getId(), newVoucherId);
            } else {
                // Trường hợp 3b: Voucher mới khác cũ → kiểm tra hợp lệ và giữ số lượng voucher mới trước
                newVoucher = voucherService.validateVoucherForReservation(newVoucherId, reservation);
                voucherService.reserveVoucher(newVoucherId);
                isReservedIncreased = true;
                log.info("[VOUCHER] Reservation {} | Đổi sang voucher {}", reservation.getId(), newVoucherId);
            }

            // Server tự tính chiết khấu — không tin tưởng số liệu từ client gửi lên
            ReservationDTO calculated = reservationService.calculateDiscount(reservation, newVoucher);

            // So sánh với client để ghi log cảnh báo nếu có bất thường
            verifyClientAmount(dto, calculated, reservation.getId());

            // Áp dụng chiết khấu vào Database
            reservationService.applyDiscount(reservation, newVoucher, calculated);

            // Giải phóng voucher cũ sau khi áp dụng thành công voucher mới
            if (!isSameVoucher && currentVoucher != null) {
                voucherService.release(currentVoucher.getId());
            }

        } catch (Exception e) {
            // Hoàn tác: nếu đã tăng số lượng giữ chỗ của voucher mới thì hoàn lại khi gặp lỗi
            if (isReservedIncreased && newVoucher != null) {
                voucherService.release(newVoucher.getId());
                log.warn("[VOUCHER] Rollback reservedQuantity voucher {} do lỗi: {}",
                        newVoucherId, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * So sánh số tiền client tính với số tiền server tính toán để phát hiện bất thường nếu có.
     */
    private void verifyClientAmount(ReservationDTO dto, ReservationDTO calculated, String reservationId) {
        if (dto.getFinalAmount() != null
                && !dto.getFinalAmount().equals(calculated.getFinalAmount())) {
            log.warn("[PAYMENT] Price mismatch | Reservation {} | client={} server={}",
                    reservationId, dto.getFinalAmount(), calculated.getFinalAmount());
        }
    }

}
