package com.example.event.service.Impl;

import com.example.event.constant.ErrorCode;
import com.example.event.constant.ShowStatus;
import com.example.event.constant.TicketTypeStatus;
import com.example.event.dto.request.CreateTicketTypeReq;
import com.example.event.dto.request.UpdateTicketTypeReq;
import com.example.event.entity.Show;
import com.example.event.entity.TicketType;
import com.example.event.exception.AppException;
import com.example.event.repository.TicketTierRepository;
import com.example.event.repository.TicketTypeRepository;
import com.example.event.service.TicketTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl implements TicketTypeService {
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTierRepository ticketTierRepository;

    @Override
    public List<TicketType> createTicketTypes(List<CreateTicketTypeReq> ticketTypesReq,
                                  Show show,
                                  String creatorId) {
        List<TicketType> ticketTypesToSave = new ArrayList<>();
        for (CreateTicketTypeReq ticketTypeReq : ticketTypesReq) {
            TicketType ticketType = new TicketType();
            ticketType.setName(ticketTypeReq.getName());
            ticketType.setTotalQuantity(ticketTypeReq.getTotalQuantity());
            ticketType.setReservedQuantity(0);
            ticketType.setSoldQuantity(0);
            ticketType.setReservedQuantity(0);
            ticketType.setStatus(TicketTypeStatus.ACTIVE);
            ticketType.setSeatMapSvg(ticketTypeReq.getSeatMapSvg());
            ticketType.setSeatingType(ticketTypeReq.getSeatingType());
            ticketType.setSectionId(ticketTypeReq.getSectionId());
            ticketType.setShow(show);

            ticketType.setCreatedAt(LocalDateTime.now());
            ticketType.setCreatedBy(creatorId);
            ticketType.setUpdatedAt(LocalDateTime.now());
            ticketType.setUpdatedBy(creatorId);
            ticketTypesToSave.add(ticketType);
        }
        return ticketTypesToSave;
    }

    @Override
    public List<TicketType> updateTicketTypes(List<UpdateTicketTypeReq> ticketTypesReq, Show show, String updatorId) {
        List<TicketType> existingTypes = ticketTypeRepository.findTicketTypesByShow_IdAndDeletedAtIsNull(show.getId());

        // 2. Xác định những ID bị xóa
        List<String> newTicketTypeIds = ticketTypesReq.stream()
                .map(UpdateTicketTypeReq::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<String> deletedTypeIds = existingTypes.stream()
                .map(TicketType::getId)
                .filter(id -> !newTicketTypeIds.contains(id))
                .collect(Collectors.toList());

        if (!deletedTypeIds.isEmpty()) {
            softDeleteTicketTypes(deletedTypeIds, updatorId);
        }

        List<TicketType> ticketTypesToSave = new ArrayList<>();
        for (UpdateTicketTypeReq ticketTypeReq : ticketTypesReq) {
            if (ticketTypeReq.getId() == null) {
                TicketType ticketType = new TicketType();
                ticketType.setName(ticketTypeReq.getName());
                ticketType.setTotalQuantity(ticketTypeReq.getTotalQuantity());
                ticketType.setSoldQuantity(0);
                ticketType.setReservedQuantity(0);
                ticketType.setStatus(TicketTypeStatus.ACTIVE);
                ticketType.setSeatingType(ticketTypeReq.getSeatingType());
                ticketType.setSeatMapSvg(ticketTypeReq.getSeatMapSvg());
                ticketType.setSectionId(ticketTypeReq.getSectionId());
                ticketType.setShow(show);

                ticketType.setCreatedAt(LocalDateTime.now());
                ticketType.setCreatedBy(updatorId);
                ticketType.setUpdatedAt(LocalDateTime.now());
                ticketType.setUpdatedBy(updatorId);
                ticketTypesToSave.add(ticketType);
            }else {
                TicketType ticketType = Optional.ofNullable(ticketTypeRepository.findTicketTypeById(ticketTypeReq.getId()))
                        .orElseThrow(() -> new AppException(ErrorCode.TICKET_TYPE_NOT_FOUND));
                ticketType.setName(ticketTypeReq.getName());
                if (ticketTypeReq.getTotalQuantity() < ticketType.getReservedQuantity()) {
                    throw new AppException(ErrorCode.TOTAL_QUANTITY_LESS_THAN_SOLD);
                }
                if (show.getStatus() != ShowStatus.DRAFT && !ticketType.getSeatingType().equals(ticketTypeReq.getSeatingType())) {
                    throw new AppException(ErrorCode.SEATING_TYPE_UPDATE_FORBIDDEN);
                }
                ticketType.setTotalQuantity(ticketTypeReq.getTotalQuantity());
                ticketType.setStatus(ticketType.getStatus());
                ticketType.setSeatingType(ticketTypeReq.getSeatingType());
                ticketType.setSectionId(ticketTypeReq.getSectionId());
                ticketType.setSeatMapSvg(ticketTypeReq.getSeatMapSvg());
                ticketType.setShow(show);

                ticketType.setUpdatedAt(LocalDateTime.now());
                ticketType.setUpdatedBy(updatorId);
                ticketTypesToSave.add(ticketType);
            }
        }
        return ticketTypesToSave;
    }

    @Transactional
    public void softDeleteTicketTypes(List<String> typeIds, String deletorId) {
        if (typeIds == null || typeIds.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();

        ticketTierRepository.softDeleteTiersByTypeIds(typeIds, now, deletorId);
        ticketTypeRepository.softDeleteTypesByIds(typeIds, now, deletorId);
    }
}
