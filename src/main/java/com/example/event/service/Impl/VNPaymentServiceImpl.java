package com.example.event.service.Impl;

import com.example.event.config.VNPayConfig;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.PaymentMethod;
import com.example.event.constant.PaymentStatus;
import com.example.event.constant.ReservationStatus;
import com.example.event.dto.ReservationDTO;
import com.example.event.entity.Payment;
import com.example.event.entity.Reservation;
import com.example.event.entity.User;
import com.example.event.entity.Voucher;
import com.example.event.exception.AppException;
import com.example.event.repository.PaymentRepository;
import com.example.event.repository.ReservationItemRepository;
import com.example.event.repository.ReservationRepository;
import com.example.event.repository.VoucherRepository;
import com.example.event.service.PaymentService;
import com.example.event.service.ReservationService;
import com.example.event.service.TicketService;
import com.example.event.service.VoucherService;
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
    private final ReservationItemRepository reservationItemRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherService voucherService;
    private final PaymentRepository paymentRepository;
    private final TicketService ticketService;
    private final SimpMessagingTemplate messagingTemplate;
    private static final Map<String, String> VNPAY_MESSAGES;

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

//    @Override
//    @Transactional
//    public String createPaymentUrl(ReservationDTO reservationDTO, HttpServletRequest httpRequest) {
//        LocalDateTime now = LocalDateTime.now();
//        Reservation reservation = Optional.ofNullable(reservationRepository.findReservationById(reservationDTO.getId()))
//                .orElseThrow(() -> {
//                    log.warn("[PAYMENT] User {} | Reservation {} không tìm thấy được đơn đặt.", reservationDTO.getUserId(), reservationDTO.getId());
//                    return new AppException(ErrorCode.RESERVATION_NOT_FOUND);
//                });
//        // valite dữ liệu reservation;
//        reservationService.validateReservationForPayment(reservation);
//
//        validateReservation(reservation, reservationDTO);
//        Voucher currentVoucher = reservation.getVoucher();
//        String newVoucherId = reservationDTO.getVoucherId();
//
//        Voucher newVoucher = null;
//        boolean isReservedIncreased = false;
//
//        try {
//            if (newVoucherId != null) {
//                newVoucher = Optional.ofNullable(voucherRepository.findVoucherById(newVoucherId))
//                        .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
//                boolean isSameVoucher = currentVoucher != null
//                        && currentVoucher.getId().equals(newVoucherId);
//                if (!isSameVoucher) {
//                    int updated = voucherRepository.increaseReservedQuantity(newVoucherId);
//                    if (updated == 0) {
//                        throw new AppException(ErrorCode.VOUCHER_EXHAUSTED);
//                    }
//                    isReservedIncreased = true;
//                    applyReservationAfterDiscount(reservation, reservationDTO, newVoucher);
//                    if (currentVoucher != null) {
//                        voucherRepository.decreaseReservedQuantity(currentVoucher.getId());
//                    }
//                }
//            } else {
//                if (currentVoucher != null) {
//                    voucherRepository.decreaseReservedQuantity(currentVoucher.getId());
//                    resetReservation(reservation);
//                }
//            }
//            Payment payment = Optional.ofNullable(findPaymentAndUpdate(reservation))
//                    .orElseGet(() -> createNewPayment(reservation, now));
//            return buildPaymentUrl(payment, httpRequest);
//        } catch (Exception e) {
//            if (newVoucher != null && isReservedIncreased) {
//                voucherRepository.decreaseReservedQuantity(newVoucher.getId());
//            }
//            throw e;
//        }
//    }

    @Override
    @Transactional
    public String createPaymentUrl(ReservationDTO reservationDTO, HttpServletRequest httpRequest) {
        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = Optional.ofNullable(reservationRepository.findReservationById(reservationDTO.getId()))
                .orElseThrow(() -> {
                    log.warn("[PAYMENT] User {} | Reservation {} không tìm thấy được đơn đặt.", reservationDTO.getUserId(), reservationDTO.getId());
                    return new AppException(ErrorCode.RESERVATION_NOT_FOUND);
                });
        // valite dữ liệu reservation;
        reservationService.validateReservationForPayment(reservation);
        // Xử lý voucher
        handleVoucherChange(reservation, reservationDTO);
        // Tạo hoặc update payment
        Payment payment = Optional.ofNullable(findPaymentAndUpdate(reservation))
                .orElseGet(() -> createNewPayment(reservation, now));
        // Build VNPay URL
        return buildPaymentUrl(payment, httpRequest);
    }

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

        // Kiểm tra chữ ký
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

    @Override
    @Transactional
    public void processPayment(Map<String, String> result) {
        // 1. Lấy thông tin thô
        String txnRef = result.get("txnRef");
        String responseCode = result.get("responseCode");
        String paymentId = extractIdFromTxnRef(txnRef);

        Payment payment = Optional.ofNullable(paymentRepository.findPaymentById(paymentId))
                .orElseThrow(() -> {
                    log.error("[PAYMENT] Không tìm thấy Payment với ID: {}", paymentId);
                    return new AppException(ErrorCode.PAYMENT_NOT_FOUND);
                });

        validatePaymentState(payment, result);
        updatePaymentDetails(payment, result, responseCode);
        handlePostPaymentBusiness(payment, responseCode);
    }

    private void validatePaymentState(Payment payment, Map<String, String> result) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("[PAYMENT] Giao dịch {} đã được xử lý trước đó (Status: {}). Bỏ qua Webhook.",
                    payment.getId(), payment.getStatus());
            throw new AppException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }

        long vnpAmount = Long.parseLong(result.get("amount")) / 100;
        if (!payment.getFinalAmount().equals(vnpAmount)) {
            log.error("[PAYMENT] Gian lận hoặc sai lệch số tiền! DB: {}, VNPAY: {}",
                    payment.getFinalAmount(), vnpAmount);
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }
    }

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

    private void handlePostPaymentBusiness(Payment payment, String responseCode) {
        Reservation reservation = payment.getReservation();
        String resId = reservation.getId();
        Map<String, String> socketData = new HashMap<>();
        socketData.put("reservationId", resId);

        if ("00".equals(responseCode)) {
            reservation.setStatus(ReservationStatus.PAID);
            reservation.setUpdatedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
            reservationRepository.save(reservation);
            ticketService.generateTickets(resId);

            socketData.put("status", "SUCCESS");
            socketData.put("message", "Thanh toán thành công");
        } else {
            socketData.put("status", "FAILED");
            socketData.put("message", VNPAY_MESSAGES.getOrDefault(responseCode, "Giao dịch thất bại"));
        }

        messagingTemplate.convertAndSend("/topic/reservations/" + resId + "/payment-response", socketData);
    }

    private String extractIdFromTxnRef(String txnRef) {
        return (txnRef != null && txnRef.contains("_")) ? txnRef.split("_")[0] : txnRef;
    }

    private void validateReservation(Reservation res, ReservationDTO dto) {
        if (LocalDateTime.now().isAfter(res.getExpiresAt()))
            throw new AppException(ErrorCode.RESERVATION_EXPIRED);
        if (!res.getTotalAmount().equals(dto.getTotalAmount()))
            throw new AppException(ErrorCode.INVALID_AMOUNT);
    }

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

    private String buildPaymentUrl(Payment payment, HttpServletRequest httpRequest) {
        String version = "2.1.0";
        String command = "pay";
        String orderType = "other";
        // Số tiền cần nhân với 100
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

        // Sắp xếp tham số và Build Query String
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

    private Payment findPaymentAndUpdate(Reservation reservation) {
        Payment payment = reservation.getPayment();
        if (payment == null) return null;
        payment.setTotalAmount(reservation.getTotalAmount());
        payment.setDiscountAmount(reservation.getDiscountAmount());
        payment.setFinalAmount(reservation.getFinalAmount());
        paymentRepository.save(payment);
        return payment;
    }

    private void handleVoucherChange(Reservation reservation, ReservationDTO dto) {
        Voucher currentVoucher = reservation.getVoucher();
        String newVoucherId = dto.getVoucherId();

        // Case 1: Không có voucher cũ lẫn mới, không làm gì
        if (newVoucherId == null && currentVoucher == null) return;

        // Case 2: Bỏ voucher
        if (newVoucherId == null) {
            log.info("[VOUCHER] Reservation {} | Bỏ voucher {}", reservation.getId(), currentVoucher.getId());
            voucherService.release(currentVoucher.getId());
            reservationService.resetDiscount(reservation);
            return;
        }

        // Case 3: Có voucher mới
        Voucher newVoucher = null;
        boolean isReservedIncreased = false;

        try {
            boolean isSameVoucher = currentVoucher != null
                    && currentVoucher.getId().equals(newVoucherId);

            if (isSameVoucher) {
                // Case 3a: Cùng voucher → chỉ recalculate, không đụng reservedQty
                newVoucher = currentVoucher;
                log.info("[VOUCHER] Reservation {} | Giữ nguyên voucher {}, recalculate",
                        reservation.getId(), newVoucherId);
            } else {
                // Case 3b: Voucher mới khác cũ → reserve mới trước
                newVoucher = voucherService.validateVoucherForReservation(newVoucherId, reservation);
                voucherService.reserveVoucher(newVoucherId);
                isReservedIncreased = true;
                log.info("[VOUCHER] Reservation {} | Đổi sang voucher {}", reservation.getId(), newVoucherId);
            }

            // Server tự tính discount — KHÔNG tin số liệu từ dto
            ReservationDTO calculated = reservationService.calculateDiscount(reservation, newVoucher);

            // So sánh với client để log anomaly
            verifyClientAmount(dto, calculated, reservation.getId());

            // Apply vào DB
            reservationService.applyDiscount(reservation, newVoucher, calculated);

            // Release voucher cũ sau khi apply thành công
            if (!isSameVoucher && currentVoucher != null) {
                voucherService.release(currentVoucher.getId());
            }

        } catch (Exception e) {
            // Rollback: nếu đã tăng reservedQty của voucher mới thì giảm lại
            if (isReservedIncreased && newVoucher != null) {
                voucherService.release(newVoucher.getId());
                log.warn("[VOUCHER] Rollback reservedQuantity voucher {} do lỗi: {}",
                        newVoucherId, e.getMessage());
            }
            throw e;
        }
    }

    private void verifyClientAmount(ReservationDTO dto, ReservationDTO calculated, String reservationId) {
        if (dto.getFinalAmount() != null
                && !dto.getFinalAmount().equals(calculated.getFinalAmount())) {
            log.warn("[PAYMENT] Price mismatch | Reservation {} | client={} server={}",
                    reservationId, dto.getFinalAmount(), calculated.getFinalAmount());
        }
    }
}
