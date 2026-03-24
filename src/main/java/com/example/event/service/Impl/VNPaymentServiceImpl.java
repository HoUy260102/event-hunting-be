package com.example.event.service.Impl;

import com.example.event.config.VNPayConfig;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.PaymentMethod;
import com.example.event.constant.PaymentStatus;
import com.example.event.constant.ReservationStatus;
import com.example.event.dto.ReservationDTO;
import com.example.event.dto.TicketSummaryDTO;
import com.example.event.entity.Payment;
import com.example.event.entity.Reservation;
import com.example.event.entity.User;
import com.example.event.exception.AppException;
import com.example.event.repository.PaymentRepository;
import com.example.event.repository.ReservationRepository;
import com.example.event.service.PaymentService;
import com.example.event.service.TicketService;
import com.example.event.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final TicketService ticketService;

    @Override
    public String createPaymentUrl(ReservationDTO reservationDTO, HttpServletRequest httpRequest) {
        Reservation reservation = Optional.ofNullable(reservationRepository.findReservationById(reservationDTO.getId()))
                .orElseThrow(() -> {
                    log.warn("[PAYMENT] User {} | Reservation {} không tìm thấy được đơn đặt.", reservationDTO.getUserId(), reservationDTO.getId());
                    return new AppException(ErrorCode.RESERVATION_NOT_FOUND);
                });
        if (LocalDateTime.now().isAfter(reservation.getExpiresAt())) {
            log.warn("[PAYMENT] User {} | Reservation {} đã hết hạn.", reservationDTO.getUserId(), reservationDTO.getId());
            throw new AppException(ErrorCode.RESERVATION_EXPIRED);
        }
        if (!reservation.getFinalAmount().equals(reservationDTO.getFinalAmount())) {
            log.warn("[PAYMENT] User {} | Reservation {} đơn đặt hàng không hợp lệ giá không khớp.", reservationDTO.getUserId(), reservationDTO.getId());
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }
        String version = "2.1.0";
        String command = "pay";
        String orderType = "other";
        // Số tiền cần nhân với 100
        long amount = reservationDTO.getFinalAmount() * 100;
        String transactionReference = reservationDTO.getId();
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

        ZonedDateTime expire = reservationDTO.getExpiresAt().atZone(zone);
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

        // 2. Kiểm tra chữ ký
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
        String reservationId = result.get("txnRef");
        String vnpayTransactionNo = result.get("transactionNo");
        String bankCode = result.get("bankCode");
        String responseCode = result.get("responseCode");
        String rawAmount = result.get("amount");
        String payDateStr = result.get("payDate");

        Long finalAmount = (rawAmount != null) ? Long.parseLong(rawAmount) / 100 : 0L;
        LocalDateTime paidAt = null;
        if (payDateStr != null && !payDateStr.isEmpty()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            paidAt = LocalDateTime.parse(payDateStr, formatter);
        }
        PaymentStatus paymentStatus = "00".equals(responseCode)
                ? PaymentStatus.SUCCESS
                : PaymentStatus.FAILED;

        ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime now = LocalDateTime.now(vnZone);

        Reservation reservation = Optional.ofNullable(reservationRepository.findReservationById(reservationId))
                .orElseThrow(() -> {
                    log.error("Reservation {} không được tìm thấy.", reservationId);
                    return new AppException(ErrorCode.RESERVATION_NOT_FOUND);
                });
        User user = reservation.getUser();

        if (!reservation.getFinalAmount().equals(finalAmount)) {
            log.error("[PAYMENT] Sai lệch số tiền! DB: {}, VNPAY: {}", reservation.getTotalAmount(), finalAmount);
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            log.warn("[PAYMENT] Đơn hàng {} đã được xử lý (Status: {})", reservation, reservation.getStatus());
            switch (reservation.getStatus()) {
                case ReservationStatus.PAID -> throw new AppException(ErrorCode.RESERVATION_ALREADY_PAID);
                case ReservationStatus.EXPIRED -> throw new AppException(ErrorCode.RESERVATION_EXPIRED);
                case ReservationStatus.CANCELLED -> throw new AppException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
                default -> throw new AppException(ErrorCode.RESERVATION_NOT_FOUND);
            }
        }

        Payment payment = new Payment();
        payment.setTotalAmount(reservation.getTotalAmount());
        payment.setFinalAmount(finalAmount);
        payment.setStatus(paymentStatus);
        payment.setTransactionNo(vnpayTransactionNo);
        payment.setBankCode(bankCode);
        payment.setMethod(PaymentMethod.VNPAY);
        payment.setPaidAt(paidAt);
        payment.setReservation(reservation);
        payment.setUser(user);

        payment.setCreatedAt(now);
        payment.setCreatedBy(user.getId());
        payment.setUpdatedAt(now);
        payment.setUpdatedBy(user.getId());
        paymentRepository.save(payment);
        log.info("[PAYMENT] User {} | Payment {} đã được tạo", user.getId(), payment.getId());

        reservation.setStatus(ReservationStatus.PAID);
        reservation.setUpdatedAt(now);
        reservation.setUpdatedBy(user.getId());
        reservationRepository.save(reservation);
        log.info("[PAYMENT] User {} | Reservation {} đã được thanh toán", user.getId(), reservation.getId());

        if ("00".equals(responseCode)) {
            List<TicketSummaryDTO> ticketDTOS = ticketService.generateTickets(reservationId);
            log.info("[PAYMENT] Thanh toán thành công và đã sinh vé cho đơn hàng: {}", reservationId);
        }
    }
}
