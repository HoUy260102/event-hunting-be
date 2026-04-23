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

    private static final String KEY_TYPE_TOTAL = "ticket_type:{show:%s}:%s:total";
    private static final String KEY_TYPE_RESERVED = "ticket_type:{show:%s}:%s:reserved";
    private static final String KEY_TIER_LIMIT = "ticket_tier:{show:%s}:%s:limit";
    private static final String KEY_TIER_RESERVED = "ticket_tier:{show:%s}:%s:reserved";
    private final TicketRepository ticketRepository;

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
            throw new AppException(ErrorCode.SHOWS_VALIDATION_ERROR, errorDetails);
        }

        String updatorId = securityUtils.getCurrentUserId();

        Event event = Optional.ofNullable(eventRepository.findEventById(eventId))
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));

        Show show = Optional.ofNullable(showRepository.findShowById(showId))
                .orElseThrow(() -> new AppException(ErrorCode.SHOW_NOT_FOUND));

        if (!show.getEvent().getId().equals(eventId)) {
            throw new AppException(ErrorCode.INVALID_EVENT_SHOW_RELATION);
        }

        if (show.getStatus() != ShowStatus.DRAFT &&
                !Objects.equals(show.getSeatMapSvg(), showReq.getSeatMapSvg())) {
            throw new AppException(ErrorCode.SEATMAP_UPDATE_FORBIDDEN);
        }

        if (show.getStatus() != ShowStatus.DRAFT &&
                !Objects.equals(show.getSeatMapType(), showReq.getSeatMapType())) {
            throw new AppException(ErrorCode.SEATMAP_TYPE_UPDATE_FORBIDDEN);
        }

        // Update show fields
        show.setMinOrder(showReq.getMinOrder());
        show.setMaxOrder(showReq.getMaxOrder());
        show.setStartTime(showReq.getStartTime());
        show.setEndTime(showReq.getEndTime());
        show.setSeatMapType(showReq.getSeatMapType());
        show.setSeatMapSvg(showReq.getSeatMapSvg());
        show.setStatus(showReq.getStatus());
        show.setUpdatedAt(LocalDateTime.now());
        show.setUpdatedBy(updatorId);

        Show savedShow = showRepository.save(show);

        List<TicketType> updatedTypes = ticketTypeService.updateTicketTypes(
                showReq.getTicketTypes(),
                savedShow,
                updatorId
        );

        ticketTypeRepository.saveAll(updatedTypes);
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
        ticketTierRepository.saveAll(tiersToSave);
        savedShow.setTicketTypes(updatedTypes);
        updateEventTime(event);
        return showMapper.toDTO(savedShow);
    }

    @Override
    @Transactional
    public ShowRegistryDTO findShowRegistryById(String id) {
//        Show show = Optional.ofNullable(showRepository.findShowById(id))
//                .orElseThrow(() -> new AppException(ErrorCode.SHOW_NOT_FOUND));
//        if (show.getDeletedAt() != null) {
//            throw new AppException(ErrorCode.SHOW_NOT_FOUND);
//        }
//        Event event = show.getEvent();
//        int totalTickets = ticketRepository.countTotalIssuedTickets(show.getId());
//        int totalCheckedInTicket = ticketRepository.countTotalCheckedInTickets(show.getId());
//        int totalRemainingTicket = ticketRepository.countTotalRemainingTickets(show.getId());
//        ShowRegistryDTO showRegistryDTO = ShowRegistryDTO.builder()
//                .eventName(event.getName())
//                .eventLocation(event.getLocation())
//                .totalTickets(totalTickets)
//                .checkedInCount(totalCheckedInTicket)
//                .remainingCount(totalRemainingTicket)
//                .startTime(show.getStartTime())
//                .endTime(show.getEndTime())
//                .build();
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
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (oldStatus == ShowStatus.ACTIVE && newStatus == ShowStatus.DRAFT) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (oldStatus == ShowStatus.CANCELLED) {
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
        return showMapper.toDTO(show);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowDTO> findShowsByEventId(String eventId) {
        List<Show> shows = showRepository.findShowsByEvent_Id(eventId);
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
        List<ShowSelectionDTO> showSelectionDTOS = showRepository.findShowsByEvent_Id(event.getId())
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

    @Transactional
    public void softDeleteShows(List<String> ids, String deletorId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        ticketTierRepository.softDeleteTiersByShowIds(ids, now, deletorId);
        ticketTypeRepository.softDeleteTypesByShowIds(ids, now, deletorId);
        showRepository.softDeleteShows(ids, now, deletorId);
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
        log.info("Đã đồng bộ dữ liệu Show {} lên Redis an toàn.", showId);
    }
}
