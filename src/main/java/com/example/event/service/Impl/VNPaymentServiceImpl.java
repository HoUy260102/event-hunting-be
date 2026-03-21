package com.example.event.service.Impl;

import com.example.event.config.VNPayConfig;
import com.example.event.constant.ErrorCode;
import com.example.event.dto.ReservationDTO;
import com.example.event.entity.Reservation;
import com.example.event.exception.AppException;
import com.example.event.repository.ReservationRepository;
import com.example.event.service.PaymentService;
import com.example.event.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            result.put("responseCode", fields.get("vnp_ResponseCode"));
        } else {
            result.put("status", "FAILED");
            result.put("message", "Chữ ký không hợp lệ");
        }
        return result;
    }
}
