package com.example.event.service.Impl;

import com.example.event.constant.AssignmentType;
import com.example.event.constant.SeatStatus;
import com.example.event.constant.SeatingType;
import com.example.event.dto.request.CreateSeatReq;
import com.example.event.dto.request.UpdateSeatReq;
import com.example.event.entity.Seat;
import com.example.event.entity.TicketType;
import com.example.event.repository.SeatRepository;
import com.example.event.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;

    @Override
    public List<Seat> createSeats(List<CreateSeatReq> seatsReq, TicketType ticketType, String creatorId) {
        List<Seat> seatsToSave = new ArrayList<>();
        for (CreateSeatReq seatReq : seatsReq) {
            Seat seat = new Seat();

            seat.setRowName(seatReq.getRowName());
            seat.setSeatNumber(seatReq.getSeatNumber());
            seat.setSeatCode(seatReq.getSeatCode());
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setTicketType(ticketType);
            seat.setAssignmentType(AssignmentType.ASSIGNED);
            seat.setSeatingType(SeatingType.SEATED);

            seat.setCreatedAt(LocalDateTime.now());
            seat.setCreatedBy(creatorId);
            seat.setUpdatedAt(LocalDateTime.now());
            seat.setUpdatedBy(creatorId);
            seatsToSave.add(seat);
        }
        return seatsToSave;
    }

    @Override
    public List<Seat> createUnassignedSeats(TicketType ticketType, String creatorId) {
        List<Seat> seatsToSave = new ArrayList<>();
        for (int i = 1; i <= ticketType.getTotalQuantity(); i++) {
            Seat seat = new Seat();

            seat.setQueueNo((long) i);
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setTicketType(ticketType);
            seat.setAssignmentType(AssignmentType.UNASSIGNED);
            seat.setSeatingType(ticketType.getSeatingType());

            seat.setCreatedAt(LocalDateTime.now());
            seat.setCreatedBy(creatorId);
            seat.setUpdatedAt(LocalDateTime.now());
            seat.setUpdatedBy(creatorId);
            seatsToSave.add(seat);
        }
        return seatsToSave;
    }

    @Override
    @Transactional
    public List<Seat> updateAssignedSeats(List<UpdateSeatReq> seatsReq, TicketType ticketType, String updatorId) {
        // Lấy danh sách hiện tại từ DB
        List<Seat> existingSeats = seatRepository.findSeatsByTicketType_IdAndDeletedAtIsNull(ticketType.getId());
        // Chuyển Request sang Map để check cho nhanh
        Map<String, UpdateSeatReq> requestMap = seatsReq.stream()
                .collect(Collectors.toMap(UpdateSeatReq::getSeatCode, req -> req));

        List<Seat> resultSeats = new ArrayList<>();
        List<String> idsToDelete = new ArrayList<>();

        for (Seat seat : existingSeats) {
            if (requestMap.containsKey(seat.getSeatCode())) {
                resultSeats.add(seat);
                requestMap.remove(seat.getSeatCode());
            } else {
                idsToDelete.add(seat.getId());
            }
        }

        // Thực hiện xóa mềm
        if (!idsToDelete.isEmpty()) {
            softDeleteSeats(idsToDelete, updatorId);
        }

        for (UpdateSeatReq newReq : requestMap.values()) {
            Seat newSeat = new Seat();
            newSeat.setSeatCode(newReq.getSeatCode());
            newSeat.setRowName(newReq.getRowName());
            newSeat.setSeatNumber(newReq.getSeatNumber());
            newSeat.setStatus(SeatStatus.AVAILABLE);
            newSeat.setAssignmentType(AssignmentType.ASSIGNED);
            newSeat.setSeatingType(SeatingType.SEATED);
            newSeat.setTicketType(ticketType);
            newSeat.setCreatedAt(LocalDateTime.now());
            newSeat.setCreatedBy(updatorId);
            newSeat.setUpdatedAt(LocalDateTime.now());
            newSeat.setUpdatedBy(updatorId);

            resultSeats.add(newSeat);
        }

        return resultSeats;
    }

    @Override
    @Transactional
    public List<Seat> updateUnassignedSeats(TicketType ticketType, int newQuantity, String updatorId) {
        // 1. Lấy danh sách ghế Unassigned hiện có của TicketType (chưa bị xóa mềm)
        List<Seat> existingSeats = seatRepository.findSeatsByTicketType_IdAndDeletedAtIsNull(ticketType.getId());
        int currentQuantity = existingSeats.size();

        List<Seat> resultSeats = new ArrayList<>(existingSeats);

        if (newQuantity > currentQuantity) {
            LocalDateTime now = LocalDateTime.now();
            for (int i = currentQuantity + 1; i <= newQuantity; i++) {
                Seat newSeat = new Seat();
                newSeat.setQueueNo((long) i);
                newSeat.setStatus(SeatStatus.AVAILABLE);
                newSeat.setAssignmentType(AssignmentType.UNASSIGNED);
                newSeat.setSeatingType(ticketType.getSeatingType());
                newSeat.setTicketType(ticketType);

                newSeat.setCreatedAt(now);
                newSeat.setCreatedBy(updatorId);
                newSeat.setUpdatedAt(now);
                newSeat.setUpdatedBy(updatorId);

                resultSeats.add(newSeat);
            }
        } else if (newQuantity < currentQuantity) {
            // TRƯỜNG HỢP GIẢM SỐ LƯỢNG: Xóa bớt ghế từ cuối danh sách
            List<Seat> seatsToDelete = existingSeats.stream()
                    .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                    .sorted(Comparator.comparing(Seat::getQueueNo).reversed())
                    .limit(currentQuantity - newQuantity)
                    .collect(Collectors.toList());

            if (!seatsToDelete.isEmpty()) {
                List<String> idsToDelete = seatsToDelete.stream().map(Seat::getId).collect(Collectors.toList());
                softDeleteSeats(idsToDelete, updatorId);

                resultSeats.removeAll(seatsToDelete);
            }
        }
        return resultSeats;
    }

    @Transactional
    public void softDeleteSeats(List<String> seatIds, String deletorId) {
        if (seatIds == null || seatIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        seatRepository.softDeleteSeatsByIds(seatIds, now, deletorId);
    }
}
