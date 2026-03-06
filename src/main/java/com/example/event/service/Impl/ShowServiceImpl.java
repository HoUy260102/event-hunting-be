package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.*;
import com.example.event.dto.ShowDTO;
import com.example.event.dto.request.*;
import com.example.event.entity.Event;
import com.example.event.entity.Show;
import com.example.event.entity.TicketTier;
import com.example.event.entity.TicketType;
import com.example.event.exception.AppException;
import com.example.event.mapper.ShowMapper;
import com.example.event.repository.EventRepository;
import com.example.event.repository.ShowRepository;
import com.example.event.repository.TicketTierRepository;
import com.example.event.repository.TicketTypeRepository;
import com.example.event.service.ShowService;
import com.example.event.service.TicketTierService;
import com.example.event.service.TicketTypeService;
import com.example.event.validation.ShowValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShowServiceImpl implements ShowService {
    private final ShowRepository showRepository;
    private final SecurityUtils securityUtils;
    private final TicketTypeService ticketTypeService;
    private final TicketTierService ticketTierService;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTierRepository ticketTierRepository;
    private final ShowMapper showMapper;
    private final EventRepository eventRepository;
    private final ShowValidator showValidator;

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

            for (int j = 0; j < showReq.getTicketTypes().size(); j++) {

                CreateTicketTypeReq typeReq = showReq.getTicketTypes().get(j);
                TicketType savedType = typesOfThisShow.get(j);

                List<TicketTier> tiers =
                        ticketTierService.createTicketTiers(
                                typeReq.getTicketTiers(),
                                savedType,
                                creatorId
                        );

                tiersToSave.addAll(tiers);
            }

            ticketTierRepository.saveAll(tiersToSave);
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

        for (int i = 0; i < showReq.getTicketTypes().size(); i++) {

            UpdateTicketTypeReq typeReq = showReq.getTicketTypes().get(i);
            TicketType savedType = updatedTypes.get(i);

            List<TicketTier> tiers =
                    ticketTierService.updateTicketTiers(
                            typeReq.getTicketTiers(),
                            savedType,
                            updatorId
                    );
            updatedTypes.get(i).setTicketTiers(tiers);
            tiersToSave.addAll(tiers);
        }

        ticketTierRepository.saveAll(tiersToSave);
        savedShow.setTicketTypes(updatedTypes);
        updateEventTime(event);
        return showMapper.toDTO(savedShow);
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
        }
        show.setStatus(newStatus);
        show.setUpdatedAt(LocalDateTime.now());
        show.setUpdatedBy(updatorId);
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
        List<TicketTier> tiersToSave = new ArrayList<>();

        for (int i = 0; i < showReq.getTicketTypes().size(); i++) {

            CreateTicketTypeReq typeReq = showReq.getTicketTypes().get(i);
            TicketType savedType = createdTypes.get(i);

            List<TicketTier> tiers =
                    ticketTierService.createTicketTiers(
                            typeReq.getTicketTiers(),
                            savedType,
                            creatorId
                    );
            createdTypes.get(i).setTicketTiers(tiers);
            tiersToSave.addAll(tiers);
        }
        ticketTierRepository.saveAll(tiersToSave);
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
                .filter((type) -> type.getStatus() == TicketTypeStatus.ACTIVE)
                .flatMap((type) -> type.getTicketTiers().stream())
                .filter((tier) -> tier.getStatus() == TicketTierStatus.ACTIVE)
                .map(tier -> tier.getPrice())
                .min(Long::compare)
                .orElse(event.getMinPrice());
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setMinPrice(minPrice);
        eventRepository.save(event);
    }
}
