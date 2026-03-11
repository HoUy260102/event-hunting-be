package com.example.event.service.Impl;

import com.example.event.constant.SeatStatus;
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
    public List<Seat> updateTicketTiers(List<UpdateSeatReq> seatsReq, TicketType ticketType, String updatorId) {
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
            newSeat.setTicketType(ticketType);
            newSeat.setCreatedAt(LocalDateTime.now());
            newSeat.setCreatedBy(updatorId);
            newSeat.setUpdatedAt(LocalDateTime.now());
            newSeat.setUpdatedBy(updatorId);

            resultSeats.add(newSeat);
        }

        return seatRepository.saveAll(resultSeats);
    }

    @Transactional
    public void softDeleteSeats(List<String> seatIds, String deletorId) {
        if (seatIds == null || seatIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        seatRepository.softDeleteSeatsByIds(seatIds, now, deletorId);
    }
}
