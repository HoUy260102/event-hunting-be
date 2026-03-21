package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.EventStatus;
import com.example.event.constant.FileStatus;
import com.example.event.constant.FileType;
import com.example.event.dto.EventDTO;
import com.example.event.dto.EventInfoDTO;
import com.example.event.dto.EventSearchPublicDTO;
import com.example.event.dto.EventSummaryDTO;
import com.example.event.dto.request.*;
import com.example.event.dto.response.KeysetPageResponse;
import com.example.event.entity.*;
import com.example.event.exception.AppException;
import com.example.event.mapper.EventMapper;
import com.example.event.repository.*;
import com.example.event.service.EventService;
import com.example.event.service.ShowService;
import com.example.event.specification.EventSpecification;
import com.example.event.validation.ShowValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    private final EventMapper eventMapper;
    private final FileRepository fileRepository;
    private final CategoryRepository categoryRepository;
    private final ProvinceRepository provinceRepository;
    private final ShowValidator showValidator;
    private final SecurityUtils securityUtils;
    private final EventRepository eventRepository;
    private final ShowService showService;
    private final ShowRepository showRepository;
    private static final Map<EventStatus, List<EventStatus>> STATUS_TRANSITIONS = new HashMap<>();

    static {
        STATUS_TRANSITIONS.put(EventStatus.DRAFT, Arrays.asList(EventStatus.PUBLISHED, EventStatus.REJECTED));
        STATUS_TRANSITIONS.put(EventStatus.PUBLISHED, Arrays.asList(EventStatus.CANCELLED));
        STATUS_TRANSITIONS.put(EventStatus.CANCELLED, Collections.emptyList());
        STATUS_TRANSITIONS.put(EventStatus.REJECTED, Collections.emptyList());
    }

    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTierRepository ticketTierRepository;

    @Override
    @Transactional
    public void createEvent(CreateEventReq createEventReq) {
        Map<String, String> errorDetails = new HashMap<>();
        List<String> fileIdsForUpdate = new ArrayList<>();

        //Check event có hop lệ
        Province province = provinceRepository.findProvinceById(createEventReq.getProvinceId());
        if (province == null) {
            errorDetails.put("provinceId", "Không tìm thấy tỉnh thành.");
        }
        Category category = categoryRepository.findCategoryById(createEventReq.getCategoryId());
        if (category == null) {
            errorDetails.put("categoryId", "Không tìm thấy chủ đề.");
        }
        File banner = fileRepository.findFileById(createEventReq.getBannerId());
        File poster = fileRepository.findFileById(createEventReq.getPosterId());
        File organizerLogo = fileRepository.findFileById(createEventReq.getOrganizerLogoId());
        if (banner == null) {
            errorDetails.put("bannerId", "Không tìm thấy được ảnh banner, vui lòng tải lại.");
        } else if (banner.getType() != FileType.IMAGE) {
            errorDetails.put("bannerId", "Banner phải là ảnh, vui lòng tải lại.");
        }
        if (poster == null) {
            errorDetails.put("posterId", "Không tìm thấy được ảnh poster, vui lòng tải lại.");
        } else if (poster.getType() != FileType.IMAGE) {
            errorDetails.put("posterId", "Poster phải là ảnh, vui lòng tải lại.");
        }
        if (organizerLogo == null) {
            errorDetails.put("organizerLogoId", "Không tìm thấy được ảnh logo nhà tổ chức, vui lòng tải lại.");
        } else if (organizerLogo.getType() != FileType.IMAGE) {
            errorDetails.put("organizerLogoId", "Logo ban tổ chức phải là ảnh, vui lòng tải lại.");
        }
        //Kiểm tra xem các media gửi lên có tồn tại không
        if (createEventReq.getMediaIds() != null && !createEventReq.getMediaIds().isEmpty()) {
            if (createEventReq.getMediaIds().size() != fileRepository.countFileByIdIn(createEventReq.getMediaIds())) {
                errorDetails.put("mediaIds", "Một số file đính kèm không tồn tại.");
            }
        }
        //Check show validate
        Map<String, String> showErrorDetails = showValidator.validate(createEventReq.getShows());
        errorDetails.putAll(showErrorDetails);
        if (!errorDetails.isEmpty()) {
            throw new AppException(ErrorCode.EVENT_VALIDATION_ERROR, errorDetails);
        }

        //Tạo event
        Event event = new Event();
        event.setName(createEventReq.getName());
        event.setDescriptionHtml(createEventReq.getDescriptionHtml());
        event.setDescriptionText(createEventReq.getDescriptionText());
        event.setLocation(createEventReq.getLocation());
        event.setStatus(EventStatus.DRAFT);
        //Tìm thời gian nhỏ nhất giữa các show
        LocalDateTime startTime = createEventReq.getShows().stream().map((show) -> show.getStartTime())
                .min((t1, t2) -> t1.isBefore(t2) ? -1 : 1)
                .orElse(null);
        //Tìm thời gian kết thúc giữa các show
        LocalDateTime endTime = createEventReq.getShows().stream().map((show) -> show.getEndTime())
                .max((t1, t2) -> t1.isBefore(t2) ? -1 : 1)
                .orElse(null);
        //Tìm min price
        Long minPrice = createEventReq.getShows().stream()
                .flatMap((show) -> show.getTicketTypes().stream())
                .flatMap((type) -> type.getTicketTiers().stream())
                .map(tier -> tier.getPrice())
                .min(Long::compare)
                .orElse(0L);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setMinPrice(minPrice);
        event.setProvince(province);
        event.setCategory(category);
        event.setBanner(banner);
        event.setPoster(poster);
        event.setOrganizerName(createEventReq.getOrganizerName());
        event.setOrganizerInfo(createEventReq.getOrganizerInfo());
        event.setOrganizerLogo(organizerLogo);

        String creatorId = securityUtils.getCurrentUserId();
        event.setCreatedAt(LocalDateTime.now());
        event.setCreatedBy(creatorId);
        event.setUpdatedAt(LocalDateTime.now());
        event.setUpdatedBy(creatorId);
        eventRepository.save(event);
        eventRepository.flush();
        //Logic sử lý file
        fileIdsForUpdate.add(banner.getId());
        fileIdsForUpdate.add(poster.getId());
        fileIdsForUpdate.add(organizerLogo.getId());
        createEventReq.getMediaIds().forEach((mediaId) -> {
            fileIdsForUpdate.add(mediaId);
        });
        // Lưu file chuyển từ pending sang active
        if (fileIdsForUpdate.size() > 0) fileRepository.activateFiles(fileIdsForUpdate, event.getId());
        //Logic lưu shows
        showService.createShows(createEventReq.getShows(), event, creatorId);
    }

    @Override
    @Transactional
    public void updateEventStatus(String id, UpdateEventStatusReq req) {
        String updatorId = securityUtils.getCurrentUserId();
        Event event = Optional.ofNullable(eventRepository.findEventById(id))
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));
        if (event.getDeletedAt() != null) {
            throw new AppException(ErrorCode.EVENT_NOT_FOUND);
        }
        EventStatus oldStatus = event.getStatus();
        EventStatus newStatus = req.getStatus();
        if (oldStatus == newStatus) return;
        List<EventStatus> validNextStatuses = STATUS_TRANSITIONS.get(oldStatus);

        if (validNextStatuses == null || !validNextStatuses.contains(newStatus)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        event.setStatus(newStatus);
        event.setUpdatedAt(LocalDateTime.now());
        event.setUpdatedBy(updatorId);
        eventRepository.save(event);
    }

    @Override
    @Transactional
    public EventDTO updateEvent(UpdateEventReq updateEventReq, String id) {
        Map<String, String> errorDetails = new HashMap<>();
        String updatorId = securityUtils.getCurrentUserId();

        Event event = Optional.ofNullable(eventRepository.findEventById(id))
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));
        if (event.getDeletedAt() != null) {
            throw new AppException(ErrorCode.EVENT_NOT_FOUND);
        }

        //Check event có hợp lệ không
        Province province = provinceRepository.findProvinceById(updateEventReq.getProvinceId());
        if (province == null) {
            errorDetails.put("provinceId", "Không tìm thấy tỉnh thành.");
        }
        Category category = categoryRepository.findCategoryById(updateEventReq.getCategoryId());
        if (category == null) {
            errorDetails.put("categoryId", "Không tìm thấy chủ đề.");
        }
        File banner = fileRepository.findFileById(updateEventReq.getBannerId());
        File poster = fileRepository.findFileById(updateEventReq.getPosterId());
        File organizerLogo = fileRepository.findFileById(updateEventReq.getOrganizerLogoId());
        if (banner == null) {
            errorDetails.put("bannerId", "Không tìm thấy được ảnh banner, vui lòng tải lại.");
        } else if (banner.getType() != FileType.IMAGE) {
            errorDetails.put("bannerId", "Banner phải là ảnh, vui lòng tải lại.");
        }
        if (poster == null) {
            errorDetails.put("posterId", "Không tìm thấy được ảnh poster, vui lòng tải lại.");
        } else if (poster.getType() != FileType.IMAGE) {
            errorDetails.put("posterId", "Poster phải là ảnh, vui lòng tải lại.");
        }
        if (organizerLogo == null) {
            errorDetails.put("organizerLogoId", "Không tìm thấy được ảnh logo nhà tổ chức, vui lòng tải lại.");
        } else if (organizerLogo.getType() != FileType.IMAGE) {
            errorDetails.put("organizerLogoId", "Logo ban tổ chức phải là ảnh, vui lòng tải lại.");
        }
        //Kiểm tra xem các media gửi lên có tồn tại không
        if (updateEventReq.getMediaIds() != null && !updateEventReq.getMediaIds().isEmpty()) {
            if (updateEventReq.getMediaIds().size() != fileRepository.countFileByIdIn(updateEventReq.getMediaIds())) {
                errorDetails.put("mediaIds", "Một số file đính kèm không tồn tại.");
            }
        }

        if (!errorDetails.isEmpty()) {
            throw new AppException(ErrorCode.EVENT_VALIDATION_ERROR, errorDetails);
        }

        List<String> oldFileIds = fileRepository.findFilesByStatusAndReferenceId(FileStatus.ACTIVE, id).stream().map(File::getId).collect(Collectors.toList());
        List<String> newFileIds = new ArrayList<>();
        if (updateEventReq.getMediaIds() != null) {
            newFileIds.addAll(updateEventReq.getMediaIds());
        }
        newFileIds.add(updateEventReq.getBannerId());
        newFileIds.add(updateEventReq.getPosterId());
        newFileIds.add(updateEventReq.getOrganizerLogoId());
        List<String> fileIdsForUpdate = extractIdsToUpdate(oldFileIds, newFileIds);
        List<String> fileIdsForDelete = extractIdsToDelete(oldFileIds, newFileIds);

        if (fileIdsForUpdate.size() > 0) fileRepository.activateFiles(fileIdsForUpdate, id);
        if (fileIdsForDelete.size() > 0) fileRepository.deleteFile(fileIdsForDelete, LocalDateTime.now());

        event.setName(updateEventReq.getName());
        event.setBanner(banner);
        event.setPoster(poster);
        event.setOrganizerLogo(organizerLogo);
        event.setDescriptionHtml(updateEventReq.getDescriptionHtml());
        event.setDescriptionText(updateEventReq.getDescriptionText());
        event.setLocation(updateEventReq.getLocation());
        event.setCategory(category);
        event.setProvince(province);
        event.setOrganizerName(updateEventReq.getOrganizerName());
        event.setOrganizerInfo(updateEventReq.getOrganizerInfo());
        event.setUpdatedAt(LocalDateTime.now());
        event.setUpdatedBy(updatorId);
        eventRepository.save(event);
        return eventMapper.toDTO(event);
    }

    @Override
    @Transactional(readOnly = true)
    public EventDTO findEventById(String id) {
        Event event = eventRepository.findEventByIdForDetails(id);
        return eventMapper.toDTO(event);
    }

    @Override
    @Transactional(readOnly = true)
    public EventInfoDTO findEventInfoById(String id) {
        Event event = eventRepository.findEventByIdForDetails(id);
        if (event.getDeletedAt() != null) {
            throw new AppException(ErrorCode.EVENT_NOT_FOUND);
        }
        List<Show> shows = event.getShows();
        shows.forEach(show -> {
            show.getTicketTypes().forEach(type -> {
                type.getTicketTiers().size();
                type.getSeats().size();
            });
        });
        event.setShows(shows);
        return eventMapper.toInfoDTO(event);
    }

    @Override
    @Transactional(readOnly = true)
    public EventSummaryDTO getEventSummaryById(String id) {
        Event event = eventRepository.findEventByIdForDetails(id);
        if (event.getDeletedAt() != null) {
            throw new AppException(ErrorCode.EVENT_NOT_FOUND);
        }
        List<Show> shows = event.getShows();
        shows.forEach(show -> {
            show.getTicketTypes().forEach(type -> {
                type.getTicketTiers().size();
            });
        });
        event.setShows(shows);
        return eventMapper.toSummaryDTO(event);
    }

    private List<String> extractIdsToUpdate(List<String> oldIds, List<String> newIds) {
        List<String> idsForUpdate = new ArrayList<>();
        Set<String> markedIds = oldIds.stream().collect(Collectors.toSet());
        for (String id : newIds) {
            if (!markedIds.contains(id)) {
                idsForUpdate.add(id);
            }
        }
        return idsForUpdate;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventDTO> getEventSearchForAdmin(EventSearchReq req) {
        Pageable pageable = PageRequest.of(req.getPage() - 1, req.getSize());
        Specification<Event> spec = (root, query, cb) -> cb.conjunction();
        if (req.getKeyword() != null && !req.getKeyword().isEmpty()) {
            spec = spec.and(Specification.anyOf(EventSpecification.hasName(req.getKeyword()),
                    EventSpecification.hasLocation(req.getKeyword()),
                    EventSpecification.hasId(req.getKeyword()),
                    EventSpecification.hasOrganizerName(req.getKeyword())));
        }
        if (req.getCategoryId() != null && !req.getCategoryId().equals("")) {
            spec = spec.and(EventSpecification.hasCategoryId(req.getCategoryId()));
        }
        if (req.getProvinceId() != null && !req.getProvinceId().equals("")) {
            spec = spec.and(EventSpecification.hasProvinceId(req.getProvinceId()));
        }
        String status = req.getStatus().toUpperCase();
        switch (status) {
            case "DELETED":
                spec = spec.and(EventSpecification.isDeleted());
                break;
            case "ALL":
                spec = spec.and(EventSpecification.isNotDeleted());
                break;
            case "DRAFT":
            case "PUBLISHED":
            case "CANCELLED":
            case "REJECTED":
                spec = spec.and(EventSpecification.hasStatus(EventStatus.valueOf(status)));
                break;
            case "UPCOMING":
                spec = spec.and(EventSpecification.isUpcoming());
                break;
            case "HAPPENING":
                spec = spec.and(EventSpecification.isHappening());
                break;
            case "FINISHED":
                spec = spec.and(EventSpecification.isFinished());
                break;
            default:
                spec = spec.and(EventSpecification.isNotDeleted());
                break;
        }
        Page<Event> events = eventRepository.findAll(spec, pageable);
        return events.map(eventMapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public KeysetPageResponse<EventSearchPublicDTO, String> getEventSearchPublic(EventSearchPublicReq req) {
        LocalDateTime now = LocalDateTime.now();
        Specification<Event> spec = Specification.where(EventSpecification.isNotDeleted());

        // Search full text;
        List<String> fullTextSearchEventIds = Collections.emptyList();
        if (req.getKeyword() != null && !req.getKeyword().isEmpty()) {
            String processedKey = Arrays.stream(req.getKeyword().trim().split("\\s+"))
                    .collect(Collectors.joining(" "));
            fullTextSearchEventIds = eventRepository.searchFullTextBoolean(processedKey)
                    .stream()
                    .map(event -> event.getId())
                    .collect(Collectors.toList());
        }
        if (req.getKeyword() != null && !req.getKeyword().isEmpty()) {
            spec = spec.and(EventSpecification.hasIdIn(fullTextSearchEventIds));
        }
        if (req.getProvinceId() != null) {
            spec = spec.and(EventSpecification.hasProvinceId(req.getProvinceId()));
        }
        if (req.getCategoryIds() != null && req.getCategoryIds().size() > 0) {
            spec = spec.and(EventSpecification.hasCategoryIds(req.getCategoryIds()));
        }
        if (req.getStartTime() != null || req.getEndTime() != null) {
            spec = spec.and(EventSpecification.isBetweenDates(req.getStartTime(), req.getEndTime()));
        }
        if (req.getMinPrice() != null) {
            spec = spec.and(EventSpecification.hasMinPrice(req.getMinPrice()));
        }
        if (req.getNextId() != null) {
            spec = spec.and(EventSpecification.hasNextId(req.getNextId()));
        }
        spec = spec.and(EventSpecification.orderByStatusAndDate(now));
        int pageSize = (req.getSize() != null) ? req.getSize() : 8;
        Pageable pageable = PageRequest.of(0, pageSize);
        Slice<Event> eventSlice = eventRepository.findAll(spec, pageable);
        List<EventSearchPublicDTO> dtos = eventSlice.getContent().stream()
                .map(eventMapper::toSearchPublicDTO)
                .collect(Collectors.toList());
        String nextKeysetId = eventSlice.hasNext() ? dtos.get(dtos.size() - 1).getId() : null;
        return new KeysetPageResponse<>(
                dtos,
                nextKeysetId,
                eventSlice.hasNext());
    }

    private List<String> extractIdsToDelete(List<String> oldIds, List<String> newIds) {
        List<String> idsForDelete = new ArrayList<>();
        Set<String> markedIds = newIds.stream().collect(Collectors.toSet());
        for (String id : oldIds) {
            if (!markedIds.contains(id)) {
                idsForDelete.add(id);
            }
        }
        return idsForDelete;
    }
}