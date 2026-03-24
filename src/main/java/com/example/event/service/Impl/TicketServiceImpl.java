package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.TicketStatus;
import com.example.event.dto.TicketDetailDTO;
import com.example.event.dto.TicketSummaryDTO;
import com.example.event.dto.request.SearchTicketPublicReq;
import com.example.event.entity.*;
import com.example.event.exception.AppException;
import com.example.event.mapper.TicketMapper;
import com.example.event.repository.*;
import com.example.event.service.TicketService;
import com.example.event.specification.TicketSpecification;
import com.github.f4b6a3.ulid.UlidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {
    private final SeatRepository seatRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTierRepository ticketTierRepository;
    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final ReservationRepository reservationRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional(readOnly = true)
    public TicketDetailDTO getTicketDetailById(String id) {
        Ticket ticket = Optional.ofNullable(ticketRepository.findTicketDetailsById(id))
                .orElseThrow(() -> new AppException(ErrorCode.TICKET_NOT_FOUND));
        return ticketMapper.toDetailDTO(ticket);
    }

    @Override
    @Transactional
    public List<TicketSummaryDTO> generateTickets(String reservationId) {
        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = Optional.ofNullable(reservationRepository.findReservationById(reservationId))
                .orElseThrow(() -> {
                    log.warn("[TICKET] Reservation {} không tìm thấy đơn hàng", reservationId);
                    return new AppException(ErrorCode.RESERVATION_NOT_FOUND);
                });
        User user = reservation.getUser();
        String creatorId = user.getId();
        Show show = reservation.getShow();
        Event event = show.getEvent();

        List<ReservationItem> reservationItems = reservation.getItems();
        List<ReservationItem> unassignedItems = reservationItems.stream()
                .filter(item -> item.getSeat() == null)
                .sorted(Comparator.comparing(o -> o.getTicketType().getId()))
                .collect(Collectors.toList());
        List<ReservationItem> assignedItems = reservationItems.stream()
                .filter(item -> item.getSeat() != null)
                .collect(Collectors.toList());
        List<Ticket> savedTickets = new ArrayList<>();
        List<String> seatIdsToUpdate = new ArrayList<>();
        Map<String, Integer> typeMap = new HashMap<>();
        Map<String, Integer> tierMap = new HashMap<>();
        reservationItems.forEach(reservationItem -> {
            typeMap.merge(reservationItem.getTicketType().getId(), reservationItem.getQuantity(), Integer::sum);
            tierMap.merge(reservationItem.getTicketTier().getId(), reservationItem.getQuantity(), Integer::sum);
        });

        // Tạo vé cho các vé không cần ghế
        for (ReservationItem unassignedItem : unassignedItems) {
            TicketType type = unassignedItem.getTicketType();
            TicketTier tier = unassignedItem.getTicketTier();
            List<Seat> selectedSeats = seatRepository.findBestAvailableUnassignedSeats(type.getId(), unassignedItem.getQuantity());
            if (selectedSeats.isEmpty() || selectedSeats.size() < unassignedItem.getQuantity()) {
                throw new AppException(ErrorCode.NOT_ENOUGH_SEATS);
            }

            seatIdsToUpdate.addAll(selectedSeats
                    .stream()
                    .map(Seat::getId)
                    .collect(Collectors.toList()));

            for (Seat seat : selectedSeats) {
                String qrCodeUlid = UlidCreator.getUlid().toString();
                Ticket ticket = new Ticket();
                ticket.setUnitPrice(tier.getPrice());
                ticket.setSection(type.getName());
                ticket.setSeatLabel(seat.getQueueNo().toString());
                ticket.setDisplayName(type.getName().trim() + "-" + seat.getQueueNo());
                ticket.setQrCode(qrCodeUlid);
                ticket.setStatus(TicketStatus.UNUSED);

                ticket.setShow(show);
                ticket.setEvent(event);
                ticket.setTicketType(type);
                ticket.setTicketTier(tier);
                ticket.setSeat(seat);
                ticket.setReservation(reservation);
                ticket.setUser(user);

                ticket.setCreatedAt(now);
                ticket.setCreatedBy(creatorId);
                ticket.setUpdatedAt(now);
                ticket.setUpdatedBy(creatorId);
                savedTickets.add(ticket);
            }
        }

        for (ReservationItem assignedItem : assignedItems) {
            TicketType type = assignedItem.getTicketType();
            TicketTier tier = assignedItem.getTicketTier();
            Seat seat = assignedItem.getSeat();
            seatIdsToUpdate.add(seat.getId());

            String qrCodeUlid = UlidCreator.getUlid().toString();
            Ticket ticket = new Ticket();
            ticket.setUnitPrice(tier.getPrice());
            ticket.setSection(type.getName());
            ticket.setSeatLabel(seat.getRowName() + "-" + seat.getSeatNumber());
            ticket.setDisplayName(type.getName().trim() + "-" + seat.getRowName() + "-" + seat.getSeatNumber());
            ticket.setQrCode(qrCodeUlid);
            ticket.setStatus(TicketStatus.UNUSED);

            ticket.setShow(show);
            ticket.setEvent(event);
            ticket.setTicketType(type);
            ticket.setTicketTier(tier);
            ticket.setSeat(seat);
            ticket.setReservation(reservation);
            ticket.setUser(user);

            ticket.setCreatedAt(now);
            ticket.setCreatedBy(creatorId);
            ticket.setUpdatedAt(now);
            ticket.setUpdatedBy(creatorId);
            savedTickets.add(ticket);
        }
        if (!seatIdsToUpdate.isEmpty()) {
            seatRepository.markSeatsAsBooked(seatIdsToUpdate, creatorId, now);
        }
        ticketRepository.saveAllAndFlush(savedTickets);
        typeMap.forEach((key, value) -> {
            int res = ticketTypeRepository.incrementSoldQuantity(key, value, now, creatorId);
            if (res == 0) {
                log.warn("[TICKET] User {} | Show {} | TicketType {} vé hết", creatorId, show.getId(), key);
                throw new AppException(ErrorCode.TICKET_TYPE_SOLD_OUT);
            }
        });
        tierMap.forEach((key, value) -> {
            int res = ticketTierRepository.incrementSoldQuantity(key, value, now, creatorId);
            if (res == 0) {
                log.warn("[TICKET] User {} | Show {} | TicketTier {} vé hết", creatorId, show.getId(), key);
                throw new AppException(ErrorCode.TICKET_TIER_SOLD_OUT);
            }
        });
        return savedTickets.stream()
                .map(ticketMapper::toSummaryDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketSummaryDTO> getAllMyTickets(SearchTicketPublicReq req) {
        String userId = securityUtils.getCurrentUserId();
        Sort sort = req.getIsFinished()
                ? Sort.by("show.startTime").descending()
                : Sort.by("show.startTime").ascending();
        Specification<Ticket> spec = Specification
                .where(TicketSpecification.fetchAll())
                .and(TicketSpecification.isNotDeleted());
        if (req.getIsFinished() == false) {
            spec = spec.and(TicketSpecification.hasUpcoming());
        } else {
            spec = spec.and(TicketSpecification.hasFinished());
        }
        spec = spec.and(TicketSpecification.hasUserId(userId));
        Pageable pageable = PageRequest.of(req.getPageNumber() - 1, req.getSize(), sort);
        Page<Ticket> tickets = ticketRepository.findAll(spec, pageable);
        return tickets.map(ticketMapper::toSummaryDTO);
    }
}
