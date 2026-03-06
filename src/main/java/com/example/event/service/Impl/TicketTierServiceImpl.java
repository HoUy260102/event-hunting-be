package com.example.event.service.Impl;

import com.example.event.constant.ErrorCode;
import com.example.event.constant.TicketTierStatus;
import com.example.event.dto.request.CreateTicketTierReq;
import com.example.event.dto.request.UpdateTicketTierReq;
import com.example.event.entity.TicketTier;
import com.example.event.entity.TicketType;
import com.example.event.exception.AppException;
import com.example.event.repository.TicketTierRepository;
import com.example.event.service.TicketTierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketTierServiceImpl implements TicketTierService {
    private final TicketTierRepository ticketTierRepository;

    @Override
    public List<TicketTier> createTicketTiers(List<CreateTicketTierReq> ticketTiersReq,
                                  TicketType ticketType,
                                  String creatorId) {
        List<TicketTier> ticketTiersToSave = new ArrayList<>();
        for (CreateTicketTierReq ticketTierReq : ticketTiersReq) {
            TicketTier ticketTier = new TicketTier();
            ticketTier.setName(ticketTierReq.getName());
            ticketTier.setPrice(ticketTierReq.getPrice());
            ticketTier.setLimitQuantity(ticketTierReq.getLimitQuantity());
            ticketTier.setSaleStartTime(ticketTierReq.getSaleStartTime());
            ticketTier.setSaleEndTime(ticketTierReq.getSaleEndTime());
            ticketTier.setDescription(ticketTierReq.getDescription());
            ticketTier.setStatus(TicketTierStatus.ACTIVE);
            ticketTier.setTicketType(ticketType);

            ticketTier.setCreatedAt(LocalDateTime.now());
            ticketTier.setCreatedBy(creatorId);
            ticketTier.setUpdatedAt(LocalDateTime.now());
            ticketTier.setUpdatedBy(creatorId);
            ticketTiersToSave.add(ticketTier);
        }
        return ticketTiersToSave;
    }

    @Override
    public List<TicketTier> updateTicketTiers(List<UpdateTicketTierReq> ticketTiersReq, TicketType ticketType, String updatorId) {
        List<TicketTier> existingTiers = ticketTierRepository.findTicketTiersByTicketType_IdAndDeletedAtIsNull(ticketType.getId());

        List<String> newTicketTierIds = ticketTiersReq.stream()
                .map(UpdateTicketTierReq::getId)
                .collect(Collectors.toList());

        // Xác định những ID bị xóa
        List<String> deletedTierIds = existingTiers.stream()
                .map(TicketTier::getId)
                .filter(id -> !newTicketTierIds.contains(id))
                .collect(Collectors.toList());

        if (!deletedTierIds.isEmpty()) {
            softDeleteTicketTiers(deletedTierIds, updatorId);
        }

        List<TicketTier> ticketTiersToSave = new ArrayList<>();
        for (UpdateTicketTierReq ticketTierReq : ticketTiersReq) {
            if (ticketTierReq.getId() == null) {
                TicketTier ticketTier = new TicketTier();
                ticketTier.setName(ticketTierReq.getName());
                ticketTier.setPrice(ticketTierReq.getPrice());
                ticketTier.setLimitQuantity(ticketTierReq.getLimitQuantity());
                ticketTier.setSoldQuantity(0);
                ticketTier.setSaleStartTime(ticketTierReq.getSaleStartTime());
                ticketTier.setSaleEndTime(ticketTierReq.getSaleEndTime());
                ticketTier.setDescription(ticketTierReq.getDescription());
                ticketTier.setStatus(TicketTierStatus.ACTIVE);
                ticketTier.setTicketType(ticketType);

                ticketTier.setCreatedAt(LocalDateTime.now());
                ticketTier.setCreatedBy(updatorId);
                ticketTier.setUpdatedAt(LocalDateTime.now());
                ticketTier.setUpdatedBy(updatorId);
                ticketTiersToSave.add(ticketTier);
            } else {
                TicketTier ticketTier = Optional.ofNullable(ticketTierRepository.findTicketTierById(ticketTierReq.getId()))
                        .orElseThrow(() -> new AppException(ErrorCode.TICKET_TIER_NOT_FOUND));
                ticketTier.setName(ticketTierReq.getName());
                ticketTier.setPrice(ticketTierReq.getPrice());
                if (ticketTierReq.getLimitQuantity() < ticketTier.getSoldQuantity()) {
                    throw new AppException(ErrorCode.LIMIT_QUANTITY_LESS_THAN_SOLD);
                }
                ticketTier.setLimitQuantity(ticketTierReq.getLimitQuantity());
                ticketTier.setSaleStartTime(ticketTierReq.getSaleStartTime());
                ticketTier.setSaleEndTime(ticketTierReq.getSaleEndTime());
                ticketTier.setDescription(ticketTierReq.getDescription());
                ticketTier.setStatus(ticketTierReq.getStatus());
                ticketTier.setTicketType(ticketType);

                ticketTier.setUpdatedAt(LocalDateTime.now());
                ticketTier.setUpdatedBy(updatorId);
                ticketTiersToSave.add(ticketTier);
            }
        }
        return ticketTiersToSave;
    }

    @Transactional
    public void softDeleteTicketTiers(List<String> tierIds, String deletorId) {
        if (tierIds == null || tierIds.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();

        ticketTierRepository.softDeleteTiersByIds(tierIds, now, deletorId);
    }
}
