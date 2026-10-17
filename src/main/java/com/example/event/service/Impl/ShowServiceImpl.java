package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.*;
import com.example.event.dto.*;
import com.example.event.dto.request.CreateShowReq;
import com.example.event.dto.request.CreateTicketTypeReq;
import com.example.event.dto.request.UpdateShowReq;
import com.example.event.dto.request.UpdateTicketTypeReq;
import com.example.event.entity.*;
import com.example.event.exception.AppException;
import com.example.event.mapper.ShowMapper;
import com.example.event.repository.*;
import com.example.event.service.*;
import com.example.event.validation.ShowValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShowServiceImpl implements ShowService {
    private final ShowRepository showRepository;
    private final SecurityUtils securityUtils;
    private final TicketTypeService ticketTypeService;
    private final TicketTierService ticketTierService;
    private final SeatService seatService;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTierRepository ticketTierRepository;
    private final SeatRepository seatRepository;
    private final ShowMapper showMapper;
    private final EventRepository eventRepository;
    private final ShowValidator showValidator;
    private final RedisTemplate<String, String> redisTemplate;
    private final ReservationRepository reservationRepository;

    private static final String KEY_TYPE_TOTAL = "ticket_type:{show:%s}:%s:total";
    private static final String KEY_TYPE_RESERVED = "ticket_type:{show:%s}:%s:reserved";
    private static final String KEY_TIER_LIMIT = "ticket_tier:{show:%s}:%s:limit";
    private static final String KEY_TIER_RESERVED = "ticket_tier:{show:%s}:%s:reserved";

    @Override
    public void createShows(List<CreateShowReq> showsReq, Event event, String creatorId) {
        List<Show> showsToSave = new ArrayList<>();
        for (CreateShowReq showReq : showsReq) {
            Show show = new Show();
            show.setMinOrder(showReq.getMinOrder());
            show.setMaxOrder(showReq.getMaxOrder());
            show.setStartTime(showReq.getStartTime());
            show.setEndTime(showReq.getEndTime());
            show.setSeatMapType(showReq.getSeatMapType());
            show.setSeatMapSvg(showReq.getSeatMapSvg());
            show.setStatus(ShowStatus.DRAFT);
            show.setEvent(event);

            show.setCreatedAt(LocalDateTime.now());
            show.setCreatedBy(creatorId);
            show.setUpdatedAt(LocalDateTime.now());
            show.setUpdatedBy(creatorId);
            showsToSave.add(show);
        }
        showRepository.saveAll(showsToSave);

        for (int i = 0; i < showsReq.size(); i++) {

            CreateShowReq showReq = showsReq.get(i);
            Show savedShow = showsToSave.get(i);

            // Tạo ticket types
            List<TicketType> typesOfThisShow =
                    ticketTypeService.createTicketTypes(
                            showReq.getTicketTypes(),
                            savedShow,
                            creatorId
                    );
            // gán Id vào ticket types
            ticketTypeRepository.saveAll(typesOfThisShow);

            // tạo ticket tiers
            List<TicketTier> tiersToSave = new ArrayList<>();
            // Tạo seats
            List<Seat> seatsToSave = new ArrayList<>();
            for (int j = 0; j < showReq.getTicketTypes().size(); j++) {

                CreateTicketTypeReq typeReq = showReq.getTicketTypes().get(j);
                TicketType savedType = typesOfThisShow.get(j);

                List<TicketTier> tiers =
                        ticketTierService.createTicketTiers(
                                typeReq.getTicketTiers(),
                                savedType,
                                creatorId
                        );
                List<Seat> currentTypeSeats = new ArrayList<>();
                if (savedShow.getSeatMapType() == SeatMapType.SECTION_WITH_SEATS && savedType.getSeatingType() == SeatingType.SEATED) {
                    // Tạo những ghế assigned
                    currentTypeSeats =
                            seatService.createSeats(
                                    typeReq.getSeats(),
                                    savedType,
                                    creatorId
                            );
                } else {
                    // Tạo những ghế unassigned
                    currentTypeSeats =
                            seatService.createUnassignedSeats(
                                    savedType,
                                    creatorId
                            );
                }
                tiersToSave.addAll(tiers);
                seatsToSave.addAll(currentTypeSeats);
            }
            if (!tiersToSave.isEmpty()) ticketTierRepository.saveAll(tiersToSave);
            if (!seatsToSave.isEmpty()) seatRepository.saveAll(seatsToSave);
        }
        log.info("[SHOW] Tạo thành công {} suất diễn cho sự kiện: eventId={}", showsReq.size(), event.getId());
    }

    @Override
    @Transactional
    public ShowDTO updateShow(UpdateShowReq showReq, String showId, String eventId) {

        Map<String, String> errorDetails =
                showValidator.validateShowForUpdate(showReq);

        if (showReq.getStartTime() != null && showReq.getEndTime() != null) {
            boolean isOverlap = showRepository.existsOverlappingShowForUpdate(
                    eventId,
                    showId,
                    showReq.getStartTime(),
                    showReq.getEndTime()
            );

            if (isOverlap) {
                errorDetails.put(
                        "startTime",
                        "Suất diễn bị trùng thời gian với một suất khác"
                );
            }
        }

        if (!errorDetails.isEmpty()) {
            log.warn("[SHOW] Xác thực cập nhật suất diễn thất bại: showId={}, eventId={}, errorDetails={}", showId, eventId, errorDetails);
            throw new AppException(ErrorCode.SHOWS_VALIDATION_ERROR, errorDetails);
        }

        String updatorId = securityUtils.getCurrentUserId();

        Event event = Optional.ofNullable(eventRepository.findEventById(eventId))
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));

        Show show = Optional.ofNullable(showRepository.findShowById(showId))
                .orElseThrow(() -> new AppException(ErrorCode.SHOW_NOT_FOUND));

        if (showReq.getVersion() != null && !showReq.getVersion().equals(show.getVersion())) {
            log.warn("[SHOW] Xung đột phiên bản khi cập nhật suất diễn: showId={}, expectedVersion={}, actualVersion={}",
                    showId, showReq.getVersion(), show.getVersion());
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }

        if (!show.getEvent().getId().equals(eventId)) {
            log.warn("[SHOW] Suất diễn không thuộc sự kiện được chỉ định: showId={}, eventId={}, actualEventId={}",
                    showId, eventId, show.getEvent().getId());
            throw new AppException(ErrorCode.INVALID_EVENT_SHOW_RELATION);
        }

        if (show.getStatus() == ShowStatus.CANCELLED) {
            log.warn("[SHOW] Không thể cập nhật suất diễn đã bị hủy: showId={}", showId);
            throw new AppException(ErrorCode.SHOW_ALREADY_CANCELLED);
        }

        // if (show.getEndTime().isBefore(LocalDateTime.now())) {
        //     throw new AppException(ErrorCode.SHOW_ENDED);
        // }

        if (show.getStatus() != ShowStatus.DRAFT &&
                !Objects.equals(show.getSeatMapSvg(), showReq.getSeatMapSvg())) {
            log.warn("[SHOW] Không được phép cập nhật sơ đồ SVG khi suất diễn không ở trạng thái DRAFT: showId={}, status={}", showId, show.getStatus());
            throw new AppException(ErrorCode.SEATMAP_UPDATE_FORBIDDEN);
        }

        if (show.getStatus() != ShowStatus.DRAFT &&
                !Objects.equals(show.getSeatMapType(), showReq.getSeatMapType())) {
            log.warn("[SHOW] Không được phép cập nhật loại sơ đồ ghế khi suất diễn không ở trạng thái DRAFT: showId={}, status={}", showId, show.getStatus());
            throw new AppException(ErrorCode.SEATMAP_TYPE_UPDATE_FORBIDDEN);
        }

        // Update show fields
        show.setMinOrder(showReq.getMinOrder());
        show.setMaxOrder(showReq.getMaxOrder());
        show.setStartTime(showReq.getStartTime());
        show.setEndTime(showReq.getEndTime());
        show.setSeatMapType(showReq.getSeatMapType());
        show.setSeatMapSvg(showReq.getSeatMapSvg());
        if (showReq.getStatus() != null) {
            show.setStatus(showReq.getStatus());
        }
        show.setUpdatedAt(LocalDateTime.now());
        show.setUpdatedBy(updatorId);

        Show savedShow = showRepository.saveAndFlush(show);

        List<TicketType> updatedTypes = ticketTypeService.updateTicketTypes(
                showReq.getTicketTypes(),
                savedShow,
                updatorId
        );

        ticketTypeRepository.saveAllAndFlush(updatedTypes);
        List<TicketTier> tiersToSave = new ArrayList<>();
        List<Seat> seatsToSave = new ArrayList<>();
        for (int i = 0; i < showReq.getTicketTypes().size(); i++) {
            UpdateTicketTypeReq typeReq = showReq.getTicketTypes().get(i);
            TicketType savedType = updatedTypes.get(i);

            List<TicketTier> tiers =
                    ticketTierService.updateTicketTiers(
                            typeReq.getTicketTiers(),
                            savedType,
                            updatorId
                    );
            List<Seat> currentTypeSeats = new ArrayList<>();
            if (savedShow.getSeatMapType() == SeatMapType.SECTION_WITH_SEATS && savedType.getSeatingType() == SeatingType.SEATED) {
                // Tạo những ghế assigned
                currentTypeSeats =
                        seatService.updateAssignedSeats(
                                typeReq.getSeats(),
                                savedType,
                                updatorId
                        );
            } else {
                // Tạo những ghế unassigned
                currentTypeSeats =
                        seatService.updateUnassignedSeats(
                                savedType,
                                typeReq.getTotalQuantity(),
                                updatorId
                        );
            }
            tiersToSave.addAll(tiers);
            seatsToSave.addAll(currentTypeSeats);
            updatedTypes.get(i).setTicketTiers(tiers);
            updatedTypes.get(i).setSeats(currentTypeSeats);
        }
        seatRepository.saveAll(seatsToSave);
        ticketTierRepository.saveAllAndFlush(tiersToSave);

        savedShow.setTicketTypes(updatedTypes);
        
        updateEventTime(event);
        if (savedShow.getStatus() == ShowStatus.ACTIVE) {
            syncShowStockToRedis(savedShow);
        }

        log.info("[SHOW] Cập nhật thông tin suất diễn thành công: showId={}, eventId={}, status={}, updatorId={}",
                savedShow.getId(), event.getId(), savedShow.getStatus(), updatorId);
        
        return showMapper.toDTO(savedShow);
    }

    @Override
    @Transactional
    public ShowRegistryDTO findShowRegistryById(String id) {
        ShowRegistryDTO showRegistryDTO = Optional.ofNullable(showRepository.findShowRegistryById(id))
                .orElseThrow(() -> new AppException(ErrorCode.SHOW_NOT_FOUND));
        return showRegistryDTO;
    }

    @Override
    @Transactional(readOnly = true)
    public ShowBookingDTO findShowBookingById(String id) {
        Show show = Optional.ofNullable(showRepository.findShowById(id))
                .orElseThrow(() -> new AppException(ErrorCode.SHOW_NOT_FOUND));
        if (show.getDeletedAt() != null) {
            throw new AppException(ErrorCode.SHOW_NOT_FOUND);
        }
        show.getTicketTypes().forEach(type -> {
            type.getTicketTiers().size();
            type.getSeats().size();
        });
        return showMapper.toBookingDTO(show);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowDetailDTO findShowDetailById(String id) {
        Show show = Optional.ofNullable(showRepository.findShowById(id))
                .orElseThrow(() -> new AppException(ErrorCode.SHOW_NOT_FOUND));
        if (show.getDeletedAt() != null) {
            throw new AppException(ErrorCode.SHOW_NOT_FOUND);
        }
        return showMapper.toDetailDTO(show);
    }

    @Override
    @Transactional
    public void updateShowStatus(String showId, ShowStatus status) {
        String updatorId = securityUtils.getCurrentUserId();
        Show show = Optional.ofNullable(showRepository.findShowById(showId))
                .orElseThrow(() -> new AppException(ErrorCode.SHOW_NOT_FOUND));
        if (show.getDeletedAt() != null) {
            throw new AppException(ErrorCode.SHOW_NOT_FOUND);
        }
        ShowStatus oldStatus = show.getStatus();
        ShowStatus newStatus = status;
        EventStatus eventStatus = show.getEvent().getStatus();
        if (eventStatus == EventStatus.DRAFT ||
                eventStatus == EventStatus.REJECTED ||
                eventStatus == EventStatus.CANCELLED) {
            log.warn("[SHOW] Không thể đổi trạng thái suất diễn do trạng thái sự kiện không hợp lệ: showId={}, eventStatus={}", showId, eventStatus);
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (oldStatus == ShowStatus.ACTIVE && newStatus == ShowStatus.DRAFT) {
            log.warn("[SHOW] Không thể chuyển trạng thái suất diễn từ ACTIVE về DRAFT: showId={}", showId);
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (oldStatus == ShowStatus.CANCELLED) {
            log.warn("[SHOW] Suất diễn đã bị hủy, không thể cập nhật trạng thái: showId={}", showId);
            throw new AppException(ErrorCode.SHOW_ALREADY_CANCELLED);
        }
        if (newStatus == ShowStatus.ACTIVE) {
            updateEventTime(show.getEvent());
            syncShowStockToRedis(show);
        }
        show.setStatus(newStatus);
        show.setUpdatedAt(LocalDateTime.now());
        show.setUpdatedBy(updatorId);
        updateEventTime(show.getEvent());
        showRepository.save(show);
        log.info("[SHOW] Cập nhật trạng thái suất diễn thành công: showId={}, oldStatus={}, newStatus={}, updatorId={}",
                showId, oldStatus, newStatus, updatorId);
    }

    @Override
    @Transactional
    public ShowDTO createShow(CreateShowReq showReq, String eventId) {

        Map<String, String> errorDetails =
                showValidator.validateShowForCreate(showReq);

        if (showReq.getStartTime() != null && showReq.getEndTime() != null) {
            boolean isOverlap = showRepository.existsOverlappingShowForCreate(
                    eventId,
                    showReq.getStartTime(),
                    showReq.getEndTime()
            );

            if (isOverlap) {
                errorDetails.put(
                        "startTime",
                        "Suất diễn bị trùng thời gian với một suất khác"
                );
            }
        }

        if (!errorDetails.isEmpty()) {
            log.warn("[SHOW] Xác thực tạo suất diễn thất bại: eventId={}, errorDetails={}", eventId, errorDetails);
            throw new AppException(ErrorCode.SHOWS_VALIDATION_ERROR, errorDetails);
        }

        String creatorId = securityUtils.getCurrentUserId();

        Event event = Optional.ofNullable(eventRepository.findEventById(eventId))
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));

        Show show = new Show();

        // Update show fields
        show.setMinOrder(showReq.getMinOrder());
        show.setMaxOrder(showReq.getMaxOrder());
        show.setStartTime(showReq.getStartTime());
        show.setEndTime(showReq.getEndTime());
        show.setSeatMapType(showReq.getSeatMapType());
        show.setSeatMapSvg(showReq.getSeatMapSvg());
        show.setStatus(ShowStatus.DRAFT);
        show.setCreatedAt(LocalDateTime.now());
        show.setCreatedBy(creatorId);
        show.setUpdatedAt(LocalDateTime.now());
        show.setUpdatedBy(creatorId);
        show.setEvent(event);
        Show savedShow = showRepository.save(show);

        List<TicketType> createdTypes = ticketTypeService.createTicketTypes(
                showReq.getTicketTypes(),
                savedShow,
                creatorId
        );

        ticketTypeRepository.saveAll(createdTypes);
        // Tạo tiers
        List<TicketTier> tiersToSave = new ArrayList<>();
        // Tạo seats
        List<Seat> seatsToSave = new ArrayList<>();
        for (int i = 0; i < showReq.getTicketTypes().size(); i++) {

            CreateTicketTypeReq typeReq = showReq.getTicketTypes().get(i);
            TicketType savedType = createdTypes.get(i);

            List<TicketTier> tiers =
                    ticketTierService.createTicketTiers(
                            typeReq.getTicketTiers(),
                            savedType,
                            creatorId
                    );
            List<Seat> currentTypeSeats = new ArrayList<>();
            if (savedShow.getSeatMapType() == SeatMapType.SECTION_WITH_SEATS && savedType.getSeatingType() == SeatingType.SEATED) {
                // Tạo những ghế assigned
                currentTypeSeats =
                        seatService.createSeats(
                                typeReq.getSeats(),
                                savedType,
                                creatorId
                        );
            } else {
                // Tạo những ghế unassigned
                currentTypeSeats =
                        seatService.createUnassignedSeats(
                                savedType,
                                creatorId
                        );
            }
            tiersToSave.addAll(tiers);
            seatsToSave.addAll(currentTypeSeats);
            createdTypes.get(i).setTicketTiers(tiers);
            createdTypes.get(i).setSeats(currentTypeSeats);
        }
        if (!tiersToSave.isEmpty()) ticketTierRepository.saveAll(tiersToSave);
        if (!seatsToSave.isEmpty()) seatRepository.saveAll(seatsToSave);
        savedShow.setTicketTypes(createdTypes);
        updateEventTime(event);
        log.info("[SHOW] Tạo đơn lẻ suất diễn thành công: showId={}, eventId={}, creatorId={}",
                savedShow.getId(), event.getId(), creatorId);
        return showMapper.toDTO(show);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowDTO> findShowsByEventId(String eventId) {
        List<Show> shows = showRepository.findByEvent_IdAndDeletedAtIsNull(eventId);
        if (shows.isEmpty()) return new ArrayList<>();
        shows.forEach(show -> {
            show.getTicketTypes().forEach(type -> {
                type.getTicketTiers().size();
            });
        });
        return shows.stream().map(showMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowDTO> findDeletedShowsByEventId(String eventId) {
        List<Show> shows = showRepository.findByEvent_IdAndDeletedAtIsNotNull(eventId);
        if (shows.isEmpty()) return new ArrayList<>();
        shows.forEach(show -> {
            show.getTicketTypes().forEach(type -> {
                type.getTicketTiers().size();
            });
        });
        return shows.stream().map(showMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ShowSelectionDTO> findShowSelectionByEventId(String eventId) {
        Event event = Optional.ofNullable(eventRepository.findEventById(eventId))
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));
        if (event.getDeletedAt() != null) {
            throw new AppException(ErrorCode.EVENT_NOT_FOUND);
        }
        List<ShowSelectionDTO> showSelectionDTOS = showRepository.findByEvent_IdAndDeletedAtIsNull(event.getId())
                .stream()
                .map(show -> {
                    ShowSelectionDTO showSelectionDTO = new ShowSelectionDTO();
                    showSelectionDTO.setId(show.getId());
                    showSelectionDTO.setStartTime(show.getStartTime());
                    showSelectionDTO.setEndTime(show.getEndTime());
                    return showSelectionDTO;
                }).collect(Collectors.toList());
        return showSelectionDTOS;
    }

    @Override
    @Transactional
    public void softDeleteShows(List<String> ids, String deletorId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        showRepository.softDeleteShows(ids, now, deletorId);
    }

    @Override
    @Transactional
    public void restoreShows(List<String> ids, String restorerId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        showRepository.restoreShows(ids, now, restorerId);
    }

    private void updateEventTime(Event event) {

        List<Show> shows = showRepository
                .findByEvent_IdAndDeletedAtIsNull(event.getId());
        if (shows.isEmpty()) return;

        LocalDateTime startTime = shows.stream()
                .filter(show -> show.getStatus() == ShowStatus.ACTIVE)
                .map(Show::getStartTime)
                .min(LocalDateTime::compareTo)
                .orElse(event.getStartTime());

        LocalDateTime endTime = shows.stream()
                .filter(show -> show.getStatus() == ShowStatus.ACTIVE)
                .map(Show::getEndTime)
                .max(LocalDateTime::compareTo)
                .orElse(event.getEndTime());

        Long minPrice = shows.stream()
                .filter((show) -> show.getStatus() == ShowStatus.ACTIVE)
                .flatMap((show) -> show.getTicketTypes().stream())
                .filter((type) -> type.getStatus() == TicketTypeStatus.ACTIVE && type.getDeletedAt() == null)
                .flatMap((type) -> type.getTicketTiers().stream())
                .filter((tier) -> tier.getStatus() == TicketTierStatus.ACTIVE && tier.getDeletedAt() == null)
                .map(tier -> tier.getPrice())
                .min(Long::compare)
                .orElse(event.getMinPrice());
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setMinPrice(minPrice);
        eventRepository.save(event);
    }

    void syncShowStockToRedis(Show show) {
        String showId = show.getId();
        for (TicketType type : show.getTicketTypes()) {
            if (show.getSeatMapType() == SeatMapType.SECTION_WITH_SEATS && type.getSeatingType() == SeatingType.SEATED)
                continue;
            String typeId = type.getId();
            redisTemplate.opsForValue().set(
                    String.format(KEY_TYPE_TOTAL, showId, typeId),
                    String.valueOf(type.getTotalQuantity())
            );

            redisTemplate.opsForValue().setIfAbsent(
                    String.format(KEY_TYPE_RESERVED, showId, typeId),
                    "0"
            );

            for (TicketTier tier : type.getTicketTiers()) {
                String tierId = tier.getId();

                redisTemplate.opsForValue().set(
                        String.format(KEY_TIER_LIMIT, showId, tierId),
                        String.valueOf(tier.getLimitQuantity())
                );

                redisTemplate.opsForValue().setIfAbsent(
                        String.format(KEY_TIER_RESERVED, showId, tierId),
                        "0"
                );
            }
        }
        log.info("[SHOW] Đã đồng bộ dữ liệu Show {} lên Redis an toàn.", showId);
    }

    @Override
    @Transactional
    public void warmUpMissingShowStockKeys(String showId) {
        Show show = Optional.ofNullable(showRepository.findShowById(showId))
                .orElse(null);

        if (show == null) {
            log.warn("[SHOW] Không tìm thấy Show {} để warm-up Redis stock.", showId);
            return;
        }

        for (TicketType type : show.getTicketTypes()) {
            if (show.getSeatMapType() == SeatMapType.SECTION_WITH_SEATS && type.getSeatingType() == SeatingType.SEATED) {
                continue;
            }

            String typeId = type.getId();
            String typeTotalKey = String.format(KEY_TYPE_TOTAL, showId, typeId);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(typeTotalKey))) {
                redisTemplate.opsForValue().set(typeTotalKey, String.valueOf(type.getTotalQuantity()));
                log.info("[SHOW] Warm-up missing Redis key: {}", typeTotalKey);
            }

            String typeReservedKey = String.format(KEY_TYPE_RESERVED, showId, typeId);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(typeReservedKey))) {
                boolean setOk = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(typeReservedKey, "0"));
                if (setOk) {
                    log.info("[SHOW] Warm-up missing Redis key: {}", typeReservedKey);
                }
            }

            for (TicketTier tier : type.getTicketTiers()) {
                String tierId = tier.getId();
                String tierLimitKey = String.format(KEY_TIER_LIMIT, showId, tierId);
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(tierLimitKey))) {
                    redisTemplate.opsForValue().set(tierLimitKey, String.valueOf(tier.getLimitQuantity()));
                    log.info("[SHOW] Warm-up missing Redis key: {}", tierLimitKey);
                }

                String tierReservedKey = String.format(KEY_TIER_RESERVED, showId, tierId);
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(tierReservedKey))) {
                    boolean setOk = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(tierReservedKey, "0"));
                    if (setOk) {
                        log.info("[SHOW] Warm-up missing Redis key: {}", tierReservedKey);
                    }
                }
            }
        }
        log.info("[SHOW] Đã warm-up missing keys cho Show {} trên Redis.", showId);
    }

    @Override
    @Transactional
    public void softDeleteShow(String id) {
        Show show = showRepository.findShowById(id);
        if (show == null) {
            throw new AppException(ErrorCode.SHOW_NOT_FOUND);
        }
        if (show.getDeletedAt() != null) {
            log.warn("[SHOW] Suất diễn đã bị xóa trước đó: showId={}", id);
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        if (show.getStatus() != ShowStatus.DRAFT) {
            log.warn("[SHOW] Chỉ được phép xóa suất diễn ở trạng thái DRAFT: showId={}, status={}", id, show.getStatus());
            throw new AppException(ErrorCode.ONLY_DRAFT_SHOW_CAN_BE_DELETED);
        }

        boolean hasReservations = reservationRepository.existsByShowIdAndDeletedAtIsNull(id);
        if (hasReservations) {
            log.warn("[SHOW] Không thể xóa suất diễn vì đã có đơn đặt vé: showId={}", id);
            throw new AppException(ErrorCode.SHOW_HAS_RESERVATIONS);
        }

        String userId = securityUtils.getCurrentUserId();
        softDeleteShows(Arrays.asList(id), userId);
        log.info("[SHOW] Xóa mềm suất diễn thành công: showId={}, userId={}", id, userId);
    }

    @Override
    @Transactional
    public void restoreShow(String id) {
        Show show = showRepository.findShowById(id);
        if (show == null) {
            throw new AppException(ErrorCode.SHOW_NOT_FOUND);
        }
        if (show.getDeletedAt() == null) {
            log.warn("[SHOW] Không thể khôi phục suất diễn chưa bị xóa: showId={}", id);
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        Event event = show.getEvent();
        if (event.getDeletedAt() != null) {
            log.warn("[SHOW] Không thể khôi phục suất diễn vì sự kiện cha đã bị xóa: showId={}, eventId={}", id, event.getId());
            throw new AppException(ErrorCode.EVENT_NOT_FOUND);
        }

        String userId = securityUtils.getCurrentUserId();
        restoreShows(Arrays.asList(id), userId);
        log.info("[SHOW] Khôi phục suất diễn thành công: showId={}, userId={}", id, userId);
    }
}
