package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.*;
import com.example.event.dto.*;
import com.example.event.dto.request.ReservationItemReq;
import com.example.event.dto.request.ReservationReq;
import com.example.event.entity.*;
import com.example.event.exception.AppException;
import com.example.event.mapper.ReservationMapper;
import com.example.event.repository.*;
import com.example.event.service.LockService;
import com.example.event.service.ReservationService;
import com.example.event.service.TicketQueueService;
import com.example.event.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationServiceImpl implements ReservationService {
    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTierRepository ticketTierRepository;
    private final UserRepository userRepository;
    private final LockService lockService;
    private final ShowRepository showRepository;
    private final SecurityUtils securityUtils;
    private final SeatRepository seatRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ReservationMapper reservationMapper;
    private final TicketQueueService ticketQueueService;
    private final VoucherRepository voucherRepository;
    private final VoucherService voucherService;

    private static final long RESERVATION_TTL_SECONDS = 300L;
    private static final int EXTRA_PERIOD_SECONDS = 30;

    @Override
    @Transactional(readOnly = true)
    public ReservationDetailDTO findReservationSuccessById(String id) {
        Reservation reservation = Optional.ofNullable(reservationRepository.findReservationDetailById(id))
                .orElseThrow(() -> new AppException(ErrorCode.RESERVATION_NOT_FOUND));
        User user = reservation.getUser();
        if (reservation.getDeletedAt() != null) {
            throw new AppException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (!securityUtils.canAccessThisResource(user.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (reservation.getStatus() != ReservationStatus.PAID) {
            switch (reservation.getStatus()){
                case ReservationStatus.EXPIRED -> throw new AppException(ErrorCode.RESERVATION_EXPIRED);
                case ReservationStatus.CANCELLED -> throw new AppException(ErrorCode.RESERVATION_EXPIRED);
                case ReservationStatus.PENDING -> throw new AppException(ErrorCode.RESERVATION_PENDING);
                default -> throw new AppException(ErrorCode.RESERVATION_NOT_FOUND);
            }
        }
        return reservationMapper.toDetailDto(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationSummaryDTO findReservationSummaryById(String id) {
        Reservation reservation = Optional.ofNullable(reservationRepository.findReservationDetailById(id))
                .orElseThrow(() -> new AppException(ErrorCode.RESERVATION_NOT_FOUND));
        User user = reservation.getUser();
        if (reservation.getDeletedAt() != null) {
            throw new AppException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (!securityUtils.canAccessThisResource(user.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (reservation.getStatus() != ReservationStatus.PAID) {
            switch (reservation.getStatus()){
                case ReservationStatus.EXPIRED -> throw new AppException(ErrorCode.RESERVATION_EXPIRED);
                case ReservationStatus.CANCELLED -> throw new AppException(ErrorCode.RESERVATION_EXPIRED);
                case ReservationStatus.PENDING -> throw new AppException(ErrorCode.RESERVATION_PENDING);
                default -> throw new AppException(ErrorCode.RESERVATION_NOT_FOUND);
            }
        }
        return reservationMapper.toSummaryDto(reservation);
    }

    @Override
    @Transactional
    public ReservationDTO createReservation(ReservationReq req) {
        LocalDateTime now = LocalDateTime.now();
        String creatorId = securityUtils.getCurrentUserId();
        log.info("[RESERVATION] Bắt đầu tạo đặt chỗ. UserId: {}, ShowId: {}, Số lượng item: {}",
                creatorId, req.getShowId(), req.getItems().size());
        Map<String, TicketType> ticketTypeMap = new HashMap<>();
        Map<String, TicketTier> ticketTierMap = new HashMap<>();

        Show show = Optional.ofNullable(showRepository.findShowById(req.getShowId()))
                .orElseThrow(() -> {
                    log.warn("[RESERVATION] Show không tồn tại: {}", req.getShowId());
                    return new AppException(ErrorCode.SHOW_NOT_FOUND);
                });
        User user = Optional.ofNullable(userRepository.findUserById(creatorId))
                .orElseThrow(() -> {
                    log.warn("[RESERVATION] User không tồn tại: {}", creatorId);
                    return new AppException(ErrorCode.USER_NOT_FOUND);
                });
        Event event = show.getEvent();

        // Check xem show có đang active
        if (show.getDeletedAt() != null) {
            log.warn("[RESERVATION] Show {} này đã bị xóa", show.getId());
            throw new AppException(ErrorCode.SHOW_NOT_FOUND);
        }
        if (!show.getStatus().equals(ShowStatus.ACTIVE)) {
            log.warn("[RESERVATION] Show {} không khả dụng. Status: {}", show.getId(), show.getStatus());
            switch (show.getStatus()) {
                case ShowStatus.CANCELLED -> throw new AppException(ErrorCode.SHOW_CANCELLED);
                case ShowStatus.POSTPONED -> throw new AppException(ErrorCode.SHOW_POSTPONED);
                default        -> throw new AppException(ErrorCode.SHOW_NOT_AVAILABLE);
            }
        } else {
            if (show.getEndTime().isBefore(now)) {
                log.warn("[RESERVATION] Show {} đã kết thúc.", show.getId());
                throw new AppException(ErrorCode.SHOW_ENDED);
            }
        }

        //Check xem event của show có hợp lệ
        if (event.getDeletedAt() != null) {
            log.warn("[RESERVATION] Event {} không khả dụng.", event.getId());
            throw new AppException(ErrorCode.EVENT_NOT_FOUND);
        }

        //Check số lượng vé đã đặt
        Integer totalQuantity = req.getItems().stream()
                .map(item -> item.getQuantity())
                .reduce(0, Integer::sum);
        if (totalQuantity > show.getMaxOrder()) {
            log.warn("[RESERVATION] User {} | Show {} - Sai số lượng vé. Total: {}, Max: {}",
                    creatorId, show.getId(), totalQuantity, show.getMaxOrder());
            throw new AppException(ErrorCode.RESERVATION_QUANTITY_EXCEEDED,
                    String.format("Bạn chỉ được đặt tối đa %d vé.", show.getMaxOrder()));
        }
        if (totalQuantity < show.getMinOrder()) {
            log.warn("[RESERVATION] User {} | Show {} - Sai số lượng vé. Total: {}, Min: {}",
                    creatorId, show.getId(), totalQuantity, show.getMinOrder());
            throw new AppException(ErrorCode.RESERVATION_QUANTITY_MINIMUM_NOT_MET,
                    String.format("Bạn cần đặt ít nhất %d vé để tiếp tục.", show.getMinOrder()));
        }

        // Danh sách các item không cần seat
        List<ReservationItemReq> unassignedItems = req.getItems().stream()
                .filter(item -> item.getSeatIds() == null || item.getSeatIds().isEmpty())
                .sorted(Comparator.comparing(ReservationItemReq::getTicketTypeId))
                .collect(Collectors.toList());
        // Danh sách các item cần seat
        List<ReservationItemReq> assignedItems = req.getItems().stream()
                .filter(item -> item.getSeatIds() != null && !item.getSeatIds().isEmpty())
                .sorted(Comparator.comparing(ReservationItemReq::getTicketTypeId))
                .collect(Collectors.toList());

        // Validate reservation items
        for (ReservationItemReq itemReq : req.getItems()) {
            TicketType type = ticketTypeRepository.findById(itemReq.getTicketTypeId())
                    .orElseThrow(() -> new AppException(ErrorCode.TICKET_TYPE_NOT_FOUND));
            TicketTier tier = ticketTierRepository.findById(itemReq.getTicketTierId())
                    .orElseThrow(() -> new AppException(ErrorCode.TICKET_TIER_NOT_FOUND));
            if (type.getDeletedAt() != null) {
                throw new AppException(ErrorCode.TICKET_TYPE_NOT_FOUND);
            }
            if (tier.getDeletedAt() != null) {
                throw new AppException(ErrorCode.TICKET_TIER_NOT_FOUND);
            }
            if (!ticketTypeMap.containsKey(type.getId())) {
                ticketTypeMap.put(type.getId(), type);
            }
            if (!ticketTierMap.containsKey(tier.getId())) {
                ticketTierMap.put(tier.getId(), tier);
            }
            if (itemReq.getQuantity() <= 0) {
                throw new AppException(ErrorCode.INVALID_QUANTITY, String.format("Số lượng vé %s của hạng vé %s phải lớn hơn 0", type.getName(), tier.getName()));
            }
            validateTicketStatusAndTime(type, tier, now);
            itemReq.setUnitPrice(tier.getPrice());
            itemReq.setTicketTypeName(type.getName());
            itemReq.setTicketTierName(tier.getName());
        }

        // Tính toán total amount
        Long totalAmount = 0L;
        totalAmount = unassignedItems.stream()
                .map(item -> item.getQuantity() * item.getUnitPrice())
                .reduce(0L, Long::sum);
        totalAmount += assignedItems.stream()
                .map(item -> item.getQuantity() * item.getUnitPrice())
                .reduce(0L, Long::sum);

        // Tiến hành lock ghế
        boolean isUnassignedReserved = false;
        boolean isSeatsLocked = false;
        List<String> seatSockettCodes = new ArrayList<>();
        try {
            // Lock các vé unassign (không có ghế)
            if (!unassignedItems.isEmpty()) {
                log.info("[RESERVATION] User {} | Show {} - Đang giữ chỗ cho {} loại vé không số ghế", creatorId, show.getId(), unassignedItems.size());
                lockService.reserveUnassignedTickets(show.getId(), unassignedItems);
                isUnassignedReserved = true;
            }
            // Lock các vé assign (có ghế)
            if (!assignedItems.isEmpty()) {
                log.info("[RESERVATION] User {} | Show {} - Đang lock {} ghế", creatorId, show.getId(), assignedItems.size());
                lockService.lockSeats(show.getId(), assignedItems, creatorId);
                isSeatsLocked = true;
            }

            // Tạo reservation
            long reservationTTLSeconds = ticketQueueService.getRemainingTimeSeconds(show.getId(), user.getId());

            Reservation reservation = new Reservation();
            reservation.setCustomerEmail(req.getCustomerEmail());
            reservation.setCustomerName(req.getCustomerName());
            reservation.setCustomerPhone(req.getCustomerPhone());
            reservation.setShow(show);
            reservation.setEvent(event);
            reservation.setTotalAmount(totalAmount);
            reservation.setFinalAmount(totalAmount);
            reservation.setExpiresAt(now.plusSeconds(reservationTTLSeconds + EXTRA_PERIOD_SECONDS));
            reservation.setStatus(ReservationStatus.PENDING);
            reservation.setUser(user);
            reservation.setCreatedBy(creatorId);
            reservation.setCreatedAt(now);
            reservation.setUpdatedBy(creatorId);
            reservation.setUpdatedAt(now);
            reservationRepository.saveAndFlush(reservation);
            log.info("[RESERVATION]User {} | Show {} - Đã lưu Reservation. ID: {}", creatorId, show.getId(), reservation.getId());

            List<ReservationItem> itemsToSave = new ArrayList<>();
            for (ReservationItemReq unassignedItem : unassignedItems) {

                // kiểm tra xem còn đủ số lượng vé không
                int typeUpdated = ticketTypeRepository.incrementReservedQuantity(
                        unassignedItem.getTicketTypeId(),
                        unassignedItem.getQuantity(),
                        now,
                        creatorId);
                // Kiểm tra xem còn đủ số lượng hạng vé không
                int tierUpdated = ticketTierRepository.incrementReservedQuantity(
                        unassignedItem.getTicketTierId(),
                        unassignedItem.getQuantity(),
                        now,
                        creatorId);
                if (typeUpdated == 0) {
                    log.error("[RESERVATION][SOLD_OUT] User: {} | Show: {} | TicketType: {} đã hết vé!",
                            creatorId, show.getId(), unassignedItem.getTicketTypeId());
                    throw new AppException(ErrorCode.TICKET_TYPE_SOLD_OUT,
                            String.format("Vé %s đã hết chỗ, vui lòng chọn loại khác.", unassignedItem.getTicketTypeName()));
                }
                if (tierUpdated == 0) {
                    log.error("[RESERVATION][SOLD_OUT] User: {} | Show: {} | TicketTier: {} đã hết vé!",
                            creatorId, show.getId(), unassignedItem.getTicketTierId());
                    throw new AppException(ErrorCode.TICKET_TIER_SOLD_OUT,
                            String.format("Hạng vé %s đã hết chỗ, vui lòng chọn loại khác.", unassignedItem.getTicketTierName()));
                }

                // Tạo item
                ReservationItem item = new ReservationItem();
                item.setTicketType(ticketTypeMap.get(unassignedItem.getTicketTypeId()));
                item.setTicketTier(ticketTierMap.get(unassignedItem.getTicketTierId()));
                item.setTicketTypeName(unassignedItem.getTicketTypeName());
                item.setTicketTierName(unassignedItem.getTicketTierName());
                item.setUnitPrice(unassignedItem.getUnitPrice());
                item.setQuantity(unassignedItem.getQuantity());
                item.setTotalPrice(unassignedItem.getUnitPrice() * unassignedItem.getQuantity());
                item.setFinalPrice(unassignedItem.getUnitPrice() * unassignedItem.getQuantity());
                item.setReservation(reservation);
                item.setCreatedBy(creatorId);
                item.setCreatedAt(now);
                item.setUpdatedBy(creatorId);
                item.setUpdatedAt(now);
                itemsToSave.add(item);
            }

            for (ReservationItemReq assignedItem : assignedItems) {
                // kiểm tra xem còn đủ số lượng vé không
                int typeUpdated = ticketTypeRepository.incrementReservedQuantity(
                        assignedItem.getTicketTypeId(),
                        assignedItem.getQuantity(),
                        now,
                        creatorId);
                // kiểm tra xem còn đủ số lượng hạng vé không
                int tierUpdated = ticketTierRepository.incrementReservedQuantity(
                        assignedItem.getTicketTierId(),
                        assignedItem.getQuantity(),
                        now,
                        creatorId);
                if (typeUpdated == 0) {
                    log.error("[RESERVATION][SOLD_OUT] User {} | Show {} | TicketType {} đã hết vé!", creatorId, show.getId(), assignedItem.getTicketTypeId());
                    throw new AppException(ErrorCode.TICKET_TYPE_SOLD_OUT,
                            String.format("Vé %s đã hết chỗ, vui lòng chọn loại khác.", assignedItem.getTicketTypeName()));
                }
                if (tierUpdated == 0) {
                    log.error("[RESERVATION][SOLD_OUT] User {} | Show {} | TicketTier {} đã hết vé!", creatorId, show.getId(), assignedItem.getTicketTierId());
                    throw new AppException(ErrorCode.TICKET_TIER_SOLD_OUT,
                            String.format("Hạng vé %s đã hết chỗ, vui lòng chọn loại khác.", assignedItem.getTicketTierName()));
                }

                for (String seatId : assignedItem.getSeatIds()) {
                    Seat seat = Optional.ofNullable(seatRepository.findSeatById(seatId))
                            .orElseThrow(() -> new AppException(ErrorCode.SEAT_NOT_FOUND));
                    // Kiểm tra ghế có người giữ chưa
                    int holdResult = seatRepository.holdSeat(seatId, creatorId, now);
                    if (holdResult == 0) {
                        log.error("[RESERVATION] User{} | Show {} - Ghế {} đã bị chiếm bởi người khác ngay trước đó!", creatorId, show.getId(), seatId);
                        throw new AppException(ErrorCode.SEAT_ALREADY_RESERVED, String.format("Hạng vé %s ghế row %s number %s đã có người đặt", assignedItem.getTicketTypeName(), seat.getRowName(), seat.getSeatNumber()));
                    }
                    seatSockettCodes.add(seat.getSeatCode());
                    // Tạo item
                    ReservationItem item = new ReservationItem();
                    item.setTicketType(ticketTypeMap.get(assignedItem.getTicketTypeId()));
                    item.setTicketTier(ticketTierMap.get(assignedItem.getTicketTierId()));
                    item.setTicketTypeName(assignedItem.getTicketTypeName());
                    item.setTicketTierName(assignedItem.getTicketTierName());
                    item.setUnitPrice(assignedItem.getUnitPrice());
                    item.setQuantity(1);
                    item.setTotalPrice(assignedItem.getUnitPrice());
                    item.setFinalPrice(assignedItem.getUnitPrice());
                    item.setSeat(seat);
                    item.setSeatCode(seat.getSeatCode());
                    item.setReservation(reservation);
                    item.setCreatedBy(creatorId);
                    item.setCreatedAt(now);
                    item.setUpdatedBy(creatorId);
                    item.setUpdatedAt(now);
                    itemsToSave.add(item);
                }
            }

            // Thêm danh sách item vào db
            if (!itemsToSave.isEmpty()) {
                reservationItemRepository.saveAll(itemsToSave);
                log.info("[RESERVATION] User {} | Show {} - Thành công! Tổng cộng {} items đã được lưu cho Reservation {}",
                       creatorId, show.getId(), itemsToSave.size(), reservation.getId());
            }

            // Bắn sự kiện websocket
            if (!seatSockettCodes.isEmpty()) {
                SeatSocketDTO seatSocketDTO = SeatSocketDTO.builder()
                        .action("HOLD")
                        .userId(creatorId)
                        .seatCodes(seatSockettCodes)
                        .build();
                messagingTemplate.convertAndSend("/topic/show/" + show.getId() + "/seats", seatSocketDTO);
            }

            reservation.setItems(itemsToSave);
            reservation.setUser(user);
            return reservationMapper.toDto(reservation);
        }
        catch (Exception e) {
            log.error("[RESERVATION] LỖI khi tạo đơn hàng. ShowId: {}, UserId: {}. Lý do: {}",
                    show.getId(), creatorId, e.getMessage());
            if (isUnassignedReserved) {
                log.info("[RESERVATION] Đang giải phóng unassigned tickets do lỗi...");
                lockService.releaseUnassignedTickets(show.getId(), unassignedItems);
            }
            if (isSeatsLocked) {
                log.info("[RESERVATION] Đang giải phóng ghế do lỗi...");
                lockService.unlockSeats(show.getId(), assignedItems);
            }
            if (e instanceof AppException) {
                throw (AppException) e;
            }
            log.error("Lỗi không xác định khi tạo đơn hàng cho show {} của User {}: ", show.getId(), creatorId, e);
            throw new AppException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationDTO findReservationAfterDiscount(String reservationId, String voucherId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESERVATION_NOT_FOUND));

        // Nếu không dùng voucher thì trả về reservation gốc
        if (voucherId == null) {
            return getReservationAfterDiscount(reservation, null);
        }
        // Nếu sử dụng voucher
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        // Validate voucher
        voucherService.validateVoucherForReservation(voucherId, reservation);
        ReservationDTO reservationDTO = getReservationAfterDiscount(reservation, voucher);
        return reservationDTO;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseReservationResources(Reservation reservation, LocalDateTime now, ReservationStatus status, String updatedBy) {
        try {
            String showId = reservation.getShow().getId();
            Voucher voucher = reservation.getVoucher();
            List<ReservationItem> items = reservation.getItems();
            // Phân loại Items
            List<ReservationItem> unassignedItems = new ArrayList<>();
            List<ReservationItem> seatedItems = new ArrayList<>();
            List<String> seatCodes = new ArrayList<>();

            Map<String, Integer> typeQtyMap = new HashMap<>();
            Map<String, Integer> tierQtyMap = new HashMap<>();

            for (ReservationItem item : items) {
                typeQtyMap.merge(item.getTicketType().getId(), item.getQuantity(), Integer::sum);
                tierQtyMap.merge(item.getTicketTier().getId(), item.getQuantity(), Integer::sum);
                if (item.getSeat() != null) {
                    seatCodes.add(item.getSeatCode());
                    seatedItems.add(item);
                } else {
                    unassignedItems.add(item);
                }
            }

            if (!unassignedItems.isEmpty()) {
                lockService.releaseUnassignedReservationItem(showId, unassignedItems);
            }
            if (!seatedItems.isEmpty()) {
                lockService.unlockSeatsReservationItem(showId, seatedItems);
            }

            // Cập nhật Database
            typeQtyMap.forEach((id, qty) ->
                    ticketTypeRepository.decrementReservedQuantity(id, qty, now, "cleanup"));
            tierQtyMap.forEach((id, qty) ->
                    ticketTierRepository.decrementReservedQuantity(id, qty, now, "cleanup"));

            if (!seatedItems.isEmpty()) {
                seatRepository.releaseAllSeatsByReservation(reservation.getId(), now);
            }

            if (voucher != null) {
                voucherRepository.decreaseReservedQuantity(voucher.getId());
            }

            if (!seatCodes.isEmpty()) {
                SeatSocketDTO seatSocketDTO = SeatSocketDTO.builder()
                        .action("UNLOCK")
                        .userId(reservation.getUser().getId())
                        .seatCodes(seatCodes)
                        .build();
                messagingTemplate.convertAndSend("/topic/show/" + showId + "/seats", seatSocketDTO);
            }

            reservation.setStatus(status);
            reservation.setUpdatedBy(updatedBy);
            reservation.setUpdatedAt(now);
            reservationRepository.save(reservation);
            log.info("Đã dọn dẹp xong đơn hàng: {}", reservation.getId());
        } catch (Exception e) {
            log.error("Lỗi nghiêm trọng khi xử lý đơn hàng {}: {}", reservation.getId(), e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional
    public void cancelReservation(String id) {
        String userId = securityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = Optional.ofNullable(reservationRepository.findReservationById(id))
                .orElseThrow(() -> {
                    log.warn("[RESERVATION] User {} | Reservation {} không tồn tại.", userId, id);
                    return new AppException(ErrorCode.RESERVATION_NOT_FOUND);
                });
        if (reservation.getStatus() == ReservationStatus.PAID ) {
            log.warn("[RESERVATION] User {} | Reservation {} đã được thanh toán.", userId, id);
            throw new AppException(ErrorCode.RESERVATION_ALREADY_PAID);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            log.warn("[RESERVATION] User {} | Reservation {} đã bị hủy trước đó.", userId, id);
            throw new AppException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
        }
        if (reservation.getStatus() == ReservationStatus.EXPIRED ) {
            log.warn("[RESERVATION] User {} | Reservation {} đã hết hạn.", userId, id);
            throw new AppException(ErrorCode.RESERVATION_EXPIRED);
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setUpdatedAt(now);
        reservation.setUpdatedBy(userId);
        reservationRepository.save(reservation);
        log.info("[RESERVATION] User {} | Reservation {} đã hủy đặt chõ", userId, id);

        // Tiến hành expired
        releaseReservationResources(reservation, now, ReservationStatus.CANCELLED, userId);
    }

    private void validateTicketStatusAndTime(TicketType type, TicketTier tier, LocalDateTime now) {
        // Validate Ticket Type Status
        if (type.getStatus() != TicketTypeStatus.ACTIVE) {
            switch (type.getStatus()) {
                case SUSPENDED -> throw new AppException(ErrorCode.TICKET_TYPE_SUSPENDED,
                        String.format("Loại vé %s hiện đang tạm ngưng bán.", type.getName()));
                case INACTIVE  -> throw new AppException(ErrorCode.TICKET_TYPE_IN_ACTIVE,
                        String.format("Loại vé %s đã ngừng hoạt động.", type.getName()));
                default        -> throw new AppException(ErrorCode.TICKET_TYPE_NOT_AVAILABLE,
                        String.format("Loại vé %s hiện không khả dụng.", type.getName()));
            }
        }

        // Validate Ticket Tier Status
        if (tier.getStatus() != TicketTierStatus.ACTIVE) {
            switch (tier.getStatus()) {
                case SUSPENDED -> throw new AppException(ErrorCode.TICKET_TIER_SUSPENDED,
                        String.format("Hạng vé %s hiện đang tạm ngưng bán.", tier.getName()));
                case INACTIVE  -> throw new AppException(ErrorCode.TICKET_TIER_IN_ACTIVE,
                        String.format("Hạng vé %s đã ngừng hoạt động.", tier.getName()));
                default        -> throw new AppException(ErrorCode.TICKET_TIER_NOT_AVAILABLE,
                        String.format("Hạng vé %s hiện không khả dụng.", tier.getName()));
            }
        }

        boolean isStarted = tier.getSaleStartTime() == null || now.isAfter(tier.getSaleStartTime());
        boolean isNotEnded = tier.getSaleEndTime() == null || now.isBefore(tier.getSaleEndTime());

        if (!isStarted || !isNotEnded) {
            throw new AppException(ErrorCode.TICKET_TIER_NOT_AVAILABLE,
                    String.format("Hạng vé %s hiện không trong thời gian mở bán.", tier.getName()));
        }
    }

    @Override
    public void validateReservationForPayment(Reservation reservation) {
        String resId = reservation.getId();

        // 1. Reservation còn hạn không
        if (LocalDateTime.now().isAfter(reservation.getExpiresAt())) {
            log.warn("Payment validation failed: Reservation {} has expired at {}", resId, reservation.getExpiresAt());
            throw new AppException(ErrorCode.RESERVATION_EXPIRED);
        }

        // 2. Trạng thái có hợp lệ để thanh toán không
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            log.warn("Payment validation failed: Reservation {} is in status {}, expected PENDING", resId, reservation.getStatus());
            throw new AppException(ErrorCode.RESERVATION_NOT_PAYABLE);
        }

        // 3. Có items không
        if (reservation.getItems() == null || reservation.getItems().isEmpty()) {
            log.warn("Payment validation failed: Reservation {} has no items (empty/null)", resId);
            throw new AppException(ErrorCode.RESERVATION_HAS_NO_ITEMS);
        }

        // 4. Số tiền hợp lệ không
        if (reservation.getFinalAmount() <= 0) {
            log.warn("Payment validation failed: Reservation {} has invalid final amount: {}", resId, reservation.getFinalAmount());
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }

        log.info("Payment validation successful for Reservation: {}", resId);
    }

    @Override
    public ReservationDTO calculateDiscount(Reservation reservation, Voucher voucher) {
        return getReservationAfterDiscount(reservation, voucher);
    }

    @Override
    public void applyDiscount(Reservation reservation, Voucher voucher, ReservationDTO calculated) {
        Map<String, ReservationItemDTO> itemMap = calculated.getItems()
                .stream()
                .collect(Collectors.toMap(ReservationItemDTO::getId, item -> item));

        for (ReservationItem item : reservation.getItems()) {
            ReservationItemDTO calc = itemMap.get(item.getId());
            if (calc == null) {
                item.setDiscountAmount(0L);
                item.setFinalPrice(item.getTotalPrice());
            } else {
                item.setDiscountAmount(calc.getDiscountAmount());
                item.setFinalPrice(calc.getFinalPrice());
            }
            reservationItemRepository.save(item);
        }

        reservation.setVoucher(voucher);
        reservation.setDiscountAmount(calculated.getDiscountAmount());
        reservation.setFinalAmount(calculated.getFinalAmount());
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
    }

    @Override
    public void resetDiscount(Reservation reservation) {
        for (ReservationItem item : reservation.getItems()) {
            item.setDiscountAmount(0L);
            item.setFinalPrice(item.getTotalPrice());
            reservationItemRepository.save(item);
        }

        reservation.setVoucher(null);
        reservation.setDiscountAmount(0L);
        reservation.setFinalAmount(reservation.getTotalAmount());
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
    }

    private ReservationDTO getReservationAfterDiscount(Reservation reservation, Voucher voucher) {
        ReservationDTO dto = reservationMapper.toDto(reservation);
        dto.setDiscountAmount(0L);
        dto.setFinalAmount(reservation.getTotalAmount());
        dto.getItems().forEach(item -> {
            item.setDiscountAmount(0L);
            item.setFinalPrice(item.getTotalPrice());
        });

        // Kiểu tra xem có voucher không
        if (voucher == null) return dto;

        // Kiểm tra điều kiện áp dụng Voucher
        long totalAmount = reservation.getTotalAmount();

        // Lọc danh sách các Item hợp lệ được áp dụng giảm giá
        List<ReservationItemDTO> eligibleItems = dto.getItems().stream()
                .filter(i -> voucher.getTicketTypes() == null
                        || voucher.getTicketTypes().isEmpty()
                        || voucher.getTicketTypes().stream().anyMatch(t -> t.getId().equals(i.getTicketTypeId())))
                .collect(Collectors.toList());

        // Nếu không có item nào thỏa mãn điều kiện voucher
        if (eligibleItems.isEmpty()) {
            dto.setDiscountAmount(0L);
            dto.setFinalAmount(totalAmount);
            return dto;
        }

        long eligibleTotal = eligibleItems.stream().mapToLong(ReservationItemDTO::getTotalPrice).sum();

        // Tính toán tổng số tiền giảm
        long totalDiscount = 0L;
        if (voucher.getDiscountType() == DiscountType.PERCENT) {
            totalDiscount = eligibleTotal * voucher.getDiscountValue() / 100;
            if (voucher.getMaxDiscountValue() != null) {
                totalDiscount = Math.min(totalDiscount, voucher.getMaxDiscountValue());
            }
        } else {
            totalDiscount = Math.min(voucher.getDiscountValue(), eligibleTotal);
        }

        // Phân bổ số tiền giảm vào từng Item hợp lệ
        long remainingDiscount = totalDiscount;
        for (int i = 0; i < eligibleItems.size(); i++) {
            ReservationItemDTO item = eligibleItems.get(i);
            long itemDiscount;

            if (i == eligibleItems.size() - 1) {
                // Item cuối cùng nhận toàn bộ phần tiền giảm còn lại để tránh sai số làm tròn
                itemDiscount = remainingDiscount;
            } else {
                // Tính tỷ lệ giảm dựa trên giá trị của item đó trong tổng số các item hợp lệ
                itemDiscount = totalDiscount * item.getTotalPrice() / eligibleTotal;
                remainingDiscount -= itemDiscount;
            }

            item.setDiscountAmount(itemDiscount);
            item.setFinalPrice(item.getTotalPrice() - itemDiscount);
        }

        // Cập nhật lại thông tin tổng quát cho Reservation DTO
        dto.setDiscountAmount(totalDiscount);
        dto.setFinalAmount(totalAmount - totalDiscount);

        log.info("Voucher {} applied to Res {}: Discount={}, Final={}",
                voucher.getId(), reservation.getId(), totalDiscount, dto.getFinalAmount());

        return dto;
    }
}
