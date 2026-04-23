package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.DiscountType;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.VoucherScope;
import com.example.event.constant.VoucherStatus;
import com.example.event.dto.VoucherDTO;
import com.example.event.dto.request.CreateVoucherReq;
import com.example.event.dto.request.SearchVoucherReq;
import com.example.event.dto.request.UpdateVoucherReq;
import com.example.event.entity.Reservation;
import com.example.event.entity.Show;
import com.example.event.entity.TicketType;
import com.example.event.entity.Voucher;
import com.example.event.exception.AppException;
import com.example.event.mapper.VoucherMapper;
import com.example.event.repository.ShowRepository;
import com.example.event.repository.TicketTypeRepository;
import com.example.event.repository.VoucherRepository;
import com.example.event.service.VoucherService;
import com.example.event.specification.VoucherSpecifiation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherServiceImpl implements VoucherService {
    private final VoucherRepository voucherRepository;
    private final ShowRepository showRepository;
    private final VoucherMapper voucherMapper;
    private final TicketTypeRepository ticketTypeRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public VoucherDTO createVoucher(CreateVoucherReq req) {
        String userId = securityUtils.getCurrentUserId();
        log.info("[CREATE VOUCHER] Request: code={}, scope={}, discountType={}",
                req.getCode(), req.getScope(), req.getDiscountType());

        // validate business
        Map<String, String> errors = validateCreate(req);
        if (!errors.isEmpty()) {
            log.warn("[CREATE VOUCHER] Validation failed: {}", errors);
            throw new AppException(ErrorCode.VOUCHER_VALIDATION_ERROR, errors);
        }

        // check duplicate code
        if (voucherRepository.existsVoucherByCode(req.getCode())) {
            log.warn("[CREATE VOUCHER] Trùng code: {}", req.getCode());
            throw new AppException(ErrorCode.VOUCHER_CODE_EXISTS);
        }

        // mapping entity
        Voucher voucher = new Voucher();
        voucher.setName(req.getName().trim());
        voucher.setCode(req.getCode().trim().toUpperCase());
        voucher.setQuantity(req.getQuantity());
        voucher.setStartTime(req.getStartTime());
        voucher.setEndTime(req.getEndTime());
        voucher.setDiscountValue(req.getDiscountValue());
        voucher.setDiscountType(req.getDiscountType());
        voucher.setMinOrderValue(req.getMinOrderValue());
        voucher.setMaxDiscountValue(req.getMaxDiscountValue());
        voucher.setScope(req.getScope());
        voucher.setStatus(VoucherStatus.DRAFT);

        // xử lý theo scope
        if (req.getScope() == VoucherScope.ORGANIZER) {

            Show show = showRepository.findById(req.getShowId())
                    .orElseThrow(() -> {
                        log.warn("🟡 [CREATE VOUCHER] Show not found: {}", req.getShowId());
                        return new AppException(ErrorCode.SHOW_NOT_FOUND);
                    });

            voucher.setShow(show);

            List<TicketType> tickets = ticketTypeRepository.findAllById(req.getTicketTypeIds());

            if (tickets.size() != req.getTicketTypeIds().size()) {
                log.warn("[CREATE VOUCHER] Some ticket types not found: {}", req.getTicketTypeIds());
                throw new AppException(ErrorCode.TICKET_TYPE_NOT_FOUND);
            }
            voucher.setTicketTypes(tickets);
        } else {
            voucher.setShow(null);
            voucher.setTicketTypes(Collections.emptyList());
        }

        // audit
        voucher.setCreatedAt(LocalDateTime.now());
        voucher.setCreatedBy(userId);
        voucher.setUpdatedAt(LocalDateTime.now());
        voucher.setUpdatedBy(userId);

        // save
        voucherRepository.save(voucher);

        // log success
        log.info("[CREATE VOUCHER] Success: id={}, code={}", voucher.getId(), voucher.getCode());
        return voucherMapper.toDTO(voucher);
    }

    @Override
    @Transactional
    public VoucherDTO updateVoucher(String id, UpdateVoucherReq req) {
        Voucher voucher = Optional.ofNullable(voucherRepository.findVoucherById(id))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        String userId = securityUtils.getCurrentUserId();
        log.info("[UPDATE VOUCHER] Request: id={}, code={}, scope={}, discountType={}",
                voucher.getId(), voucher.getCode(), voucher.getScope(), voucher.getDiscountType());

        // validate business
        Map<String, String> errors = validateUpdate(req, voucher);

        // Kiểm tra code có tồn tại không
        if (voucherRepository.existsByCodeAndIdNot(req.getCode().trim().toUpperCase(), id)) {
            log.warn("[UPDATE VOUCHER] Duplicate code detected: code={}, id={}",
                    req.getCode(), id);
            throw new AppException(ErrorCode.VOUCHER_CODE_EXISTS, errors);
        }

        if (!errors.isEmpty()) {
            log.warn("[UPDATE VOUCHER] Validation failed: {}", errors);
            throw new AppException(ErrorCode.VOUCHER_VALIDATION_ERROR, errors);
        }

        // Update voucher
        voucher.setName(req.getName().trim());
        voucher.setCode(req.getCode().trim().toUpperCase());
        voucher.setQuantity(req.getQuantity());
        voucher.setStartTime(req.getStartTime());
        voucher.setEndTime(req.getEndTime());
        voucher.setDiscountValue(req.getDiscountValue());
        voucher.setMinOrderValue(req.getMinOrderValue());
        voucher.setDiscountType(req.getDiscountType());
        voucher.setStatus(req.getStatus());
        if (req.getDiscountType() == DiscountType.VALUE) {
            voucher.setMaxDiscountValue(null);
        } else {
            voucher.setMaxDiscountValue(req.getMaxDiscountValue());
        }
        // ticket
        if (voucher.getScope() == VoucherScope.ORGANIZER) {

            List<TicketType> tickets = ticketTypeRepository.findAllById(req.getTicketTypeIds());

            if (tickets.size() != req.getTicketTypeIds().size()) {
                log.warn("[UPDATE VOUCHER] Some ticket types not found: {}", req.getTicketTypeIds());
                throw new AppException(ErrorCode.TICKET_TYPE_NOT_FOUND);
            }

            voucher.setTicketTypes(tickets);
        } else {
            voucher.setTicketTypes(Collections.emptyList());
        }

        voucher.setUpdatedAt(LocalDateTime.now());
        voucher.setUpdatedBy(userId);

        voucherRepository.save(voucher);

        log.info("[UPDATE VOUCHER] Success: id={}, newCode={}",
                voucher.getId(), voucher.getCode());

        return voucherMapper.toDTO(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherDTO findVoucherById(String id) {
        Voucher voucher = Optional.ofNullable(voucherRepository.findVoucherById(id))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        return voucherMapper.toDTO(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherDTO findVoucherOfShowByCode(String showId, String code) {
        Show show = Optional.ofNullable(showRepository.findShowById(showId))
                .orElseThrow(() -> new AppException(ErrorCode.SHOW_NOT_FOUND));
        Voucher voucher = Optional.ofNullable(voucherRepository.findVoucherByCode(code))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        // validate voucher
        validateVoucher(voucher);
        // Kiểm tra xem voucher đó có phải của show đó hay không
        if (voucher.getScope() == VoucherScope.ORGANIZER && !voucher.getShow().getId().equals(show.getId())) {
            throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }
        return voucherMapper.toDTO(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoucherDTO> findVoucherByShowIdOrVoucherSystem(String showId) {
        List<Voucher> vouchers = voucherRepository.findVoucherByShowIdOrSystem(showId);
        return vouchers.stream()
                .map(voucherMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VoucherDTO> getVouchersSearch(SearchVoucherReq req) {
        Pageable pageable = PageRequest.of(req.getPage() - 1, req.getSize());
        Specification<Voucher> spec = (root, query, cb) -> cb.conjunction();
        if (req.getKeyword() != null && !req.getKeyword().isEmpty()) {
            spec = spec.and(Specification.anyOf(VoucherSpecifiation.hasName(req.getKeyword()),
                    VoucherSpecifiation.hasCode(req.getKeyword()),
                    VoucherSpecifiation.hasId(req.getKeyword()),
                    VoucherSpecifiation.hasShowId(req.getKeyword())));
        }
        String status = req.getStatus().toUpperCase();
        switch (status) {
            case "DELETED":
                spec = spec.and(VoucherSpecifiation.isDeleted());
                break;

            case "ALL":
                spec = spec.and(VoucherSpecifiation.isNotDeleted());
                break;

            default:
                VoucherStatus statusEnum = VoucherStatus.valueOf(status);
                spec = spec.and(VoucherSpecifiation.hasStatus(statusEnum))
                        .and(VoucherSpecifiation.isNotDeleted());
                break;
        }
        Page<Voucher> vouchers = voucherRepository.findAll(spec, pageable);
        return vouchers.map(voucherMapper::toDTO);
    }

    @Override
    public void deleteVoucher(String id) {
        String deletorId = securityUtils.getCurrentUserId();
        Voucher voucher = Optional.ofNullable(voucherRepository.findVoucherById(id))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        if (voucher.getDeletedAt() != null) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_DELETED);
        }
        voucher.setName(voucher.getName() + "-deleted-" + System.currentTimeMillis());
        voucher.setCode(voucher.getCode() + "-deleted-" + System.currentTimeMillis());
        voucher.setUpdatedAt(LocalDateTime.now());
        voucher.setUpdatedBy(deletorId);
        voucher.setDeletedAt(LocalDateTime.now());
        voucher.setDeletedBy(deletorId);
        voucherRepository.save(voucher);
    }

    @Override
    public void restoreVoucher(String id) {
        String restorId = securityUtils.getCurrentUserId();
        Voucher voucher = Optional.ofNullable(voucherRepository.findVoucherById(id))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        if (voucher.getDeletedAt() == null) {
            throw new AppException(ErrorCode.VOUCHER_NOT_IN_TRASH);
        }
        String name = voucher.getName().replaceAll("-deleted-\\d+$", "");
        String code = voucher.getCode().replaceAll("-deleted-\\d+$", "");
        if (voucherRepository.existsByNameAndIdNot(name, id) ||
                voucherRepository.existsByCodeAndIdNot(code, id)) {
            throw new AppException(ErrorCode.CATEGORY_EXISTS);
        }
        voucher.setName(name);
        voucher.setCode(code);
        voucher.setUpdatedAt(LocalDateTime.now());
        voucher.setUpdatedBy(restorId);
        voucher.setDeletedAt(null);
        voucher.setDeletedBy(null);
        voucherRepository.save(voucher);
    }

    @Override
    public Voucher validateVoucherForReservation(String voucherId, Reservation reservation) {
        Voucher voucher = Optional.ofNullable(voucherRepository.findVoucherById(voucherId))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        // 1. Validate basic voucher.
        validateVoucher(voucher);

        // 2. Scope — voucher của show khác không dùng được
        if (voucher.getScope() == VoucherScope.ORGANIZER) {
            String voucherShowId = voucher.getShow() != null ? voucher.getShow().getId() : null;
            String reservationShowId = reservation.getShow() != null ? reservation.getShow().getId() : null;
            if (voucherShowId == null || !voucherShowId.equals(reservationShowId))
                throw new AppException(ErrorCode.VOUCHER_NOT_APPLICABLE);
        }

        // 3. minOrderValue
        if (reservation.getTotalAmount() < voucher.getMinOrderValue())
            throw new AppException(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET);

        return voucher;
    }

    @Override
    public void validateVoucher(Voucher voucher) {
        LocalDateTime now = LocalDateTime.now();

        // 1. Trạng thái
        if (voucher.getStatus() != VoucherStatus.ACTIVE)
            throw new AppException(ErrorCode.VOUCHER_NOT_ACTIVE);

        // 2. Thời gian hiệu lực
        if (now.isBefore(voucher.getStartTime()))
            throw new AppException(ErrorCode.VOUCHER_NOT_STARTED);
        if (now.isAfter(voucher.getEndTime()))
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);

        // 3. Kiểm tra số lượng
        if (voucher.getReservedQuantity() >= voucher.getQuantity()) {
            throw new AppException(ErrorCode.VOUCHER_EXHAUSTED);
        }
    }

    @Override
    public void reserveVoucher(String voucherId) {
        int updated = voucherRepository.increaseReservedQuantity(voucherId);
        if (updated == 0)
            throw new AppException(ErrorCode.VOUCHER_EXHAUSTED);
    }

    @Override
    public void release(String voucherId) {
        voucherRepository.decreaseReservedQuantity(voucherId);
    }

    public Map<String, String> validateCreate(CreateVoucherReq req) {

        Map<String, String> errors = new HashMap<>();

        if (!req.getEndTime().isAfter(req.getStartTime())) {
            errors.put("endTime", "Ngày kết thúc phải sau ngày bắt đầu");
        }

        // discount
        if (req.getDiscountType() == DiscountType.PERCENT) {
            if (req.getDiscountValue() > 100) {
                errors.put("discountValue", "Phần trăm giảm không được > 100%");
            }

            if (req.getMaxDiscountValue() != null && req.getMaxDiscountValue() <= 0) {
                errors.put("maxDiscountValue", "Giảm tối đa phải > 0");
            }
        }

        if (req.getDiscountType() == DiscountType.VALUE) {
            if (req.getMaxDiscountValue() != null) {
                errors.put("maxDiscountValue", "VALUE không dùng max discount");
            }
        }

        // scope
        if (req.getScope() == VoucherScope.ORGANIZER) {
            if (req.getShowId() == null || req.getShowId().isEmpty()) {
                errors.put("showId", "Vui lòng chọn suất diễn");
            }

            if (req.getTicketTypeIds() == null || req.getTicketTypeIds().isEmpty()) {
                errors.put("ticketTypeIds", "Vui lòng chọn ít nhất một loại vé");
            }
        }

        if (req.getScope() == VoucherScope.SYSTEM) {
            if (req.getShowId() != null) {
                errors.put("showId", "Voucher hệ thống không được có show");
            }

            if (req.getTicketTypeIds() != null && !req.getTicketTypeIds().isEmpty()) {
                errors.put("ticketTypeIds", "Voucher hệ thống không áp dụng theo loại vé");
            }
        }

        return errors;
    }

    public Map<String, String> validateUpdate(UpdateVoucherReq req, Voucher voucher) {

        Map<String, String> errors = new HashMap<>();

        if (!req.getEndTime().isAfter(req.getStartTime())) {
            errors.put("endTime", "Ngày kết thúc phải sau ngày bắt đầu");
        }

        boolean isStarted = voucher.getStartTime().isBefore(LocalDateTime.now());

        DiscountType type = req.getDiscountType() != null
                ? req.getDiscountType()
                : voucher.getDiscountType();

        if (type == DiscountType.PERCENT) {

            if (req.getDiscountValue() > 100) {
                errors.put("discountValue", "Phần trăm giảm không được > 100%");
            }

            if (req.getMaxDiscountValue() != null && req.getMaxDiscountValue() <= 0) {
                errors.put("maxDiscountValue", "Giảm tối đa phải > 0");
            }
        }

        if (type == DiscountType.VALUE) {

            if (req.getMaxDiscountValue() != null) {
                errors.put("maxDiscountValue", "VALUE không dùng max discount");
            }
        }

        // SCOPE
        if (voucher.getScope() == VoucherScope.ORGANIZER) {

            if (req.getTicketTypeIds() == null || req.getTicketTypeIds().isEmpty()) {
                errors.put("ticketTypeIds", "Vui lòng chọn ít nhất một loại vé");
            }

        } else {
            if (req.getTicketTypeIds() != null && !req.getTicketTypeIds().isEmpty()) {
                errors.put("ticketTypeIds", "Voucher hệ thống không áp dụng theo loại vé");
            }
        }

        if (req.getStatus() != null) {
            if (voucher.getStatus() == VoucherStatus.ACTIVE
                    && req.getStatus() == VoucherStatus.DRAFT) {

                if (voucher.getReservedQuantity() > 0) {
                    errors.put("status", "Voucher đã có người giữ, không thể chuyển về DRAFT");
                }
            }
        }

        // Kiểm tra việc update số lượng
        if (req.getQuantity() < voucher.getReservedQuantity()) {
            errors.put("quantity", "Số lượng phải lớn hơn " + voucher.getReservedQuantity());
        }

        if (isStarted && voucher.getStatus() == VoucherStatus.ACTIVE) {

            // không cho đổi loại giảm
            if (req.getDiscountType() != null &&
                    req.getDiscountType() != voucher.getDiscountType()) {
                errors.put("discountType", "Voucher đã bắt đầu, không được đổi loại giảm giá");
            }

            // không cho đổi giá trị giảm
            if (!Objects.equals(req.getDiscountValue(), voucher.getDiscountValue())) {
                errors.put("discountValue", "Voucher đã bắt đầu, không được đổi giá trị giảm");
            }

            // không cho đổi min order
            if (!Objects.equals(req.getMinOrderValue(), voucher.getMinOrderValue())) {
                errors.put("minOrderValue", "Voucher đã bắt đầu, không được đổi giá trị tối thiểu");
            }

            // không cho đổi max discount
            if (!Objects.equals(req.getMaxDiscountValue(), voucher.getMaxDiscountValue())) {
                errors.put("maxDiscountValue", "Voucher đã bắt đầu, không được đổi giảm tối đa");
            }

            // không cho đổi ticket
            if (voucher.getScope() == VoucherScope.ORGANIZER &&
                    !Objects.equals(req.getTicketTypeIds(),
                            voucher.getTicketTypes().stream().map(TicketType::getId).collect(Collectors.toList()))) {
                errors.put("ticketTypeIds", "Voucher đã bắt đầu, không được đổi loại vé");
            }

            // ⚠quantity: chỉ cho tăng
            if (req.getQuantity() < voucher.getQuantity()) {
                errors.put("quantity", "Không được giảm số lượng khi voucher đã bắt đầu");
            }

            // không cho sửa startDate
            if (!req.getStartTime().equals(voucher.getStartTime())) {
                errors.put("startTime", "Voucher đã bắt đầu, không được đổi ngày bắt đầu");
            }
        }

        return errors;
    }


}
