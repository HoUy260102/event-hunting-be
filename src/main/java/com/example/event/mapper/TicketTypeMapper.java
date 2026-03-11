package com.example.event.mapper;

import com.example.event.constant.TicketTierStatus;
import com.example.event.constant.TicketTypeStatus;
import com.example.event.dto.TicketTierSummaryDTO;
import com.example.event.dto.TicketTypeDTO;
import com.example.event.dto.TicketTypeInfoDTO;
import com.example.event.dto.TicketTypeSummaryDTO;
import com.example.event.entity.TicketTier;
import com.example.event.entity.TicketType;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TicketTypeMapper {
    private final ModelMapper modelMapper;
    private final TicketTierMapper ticketTierMapper;
    private final SeatMapper seatMapper;

    public TicketTypeDTO toDTO(TicketType ticketType) {
        TicketTypeDTO ticketTypeDTO = modelMapper.map(ticketType, TicketTypeDTO.class);
        ticketTypeDTO.setTicketTiers(ticketType.getTicketTiers().stream()
                .filter((ticketTier -> ticketTier.getDeletedAt() == null))
                .map((ticketTierMapper::toDTO))
                .collect(Collectors.toList()));
        ticketTypeDTO.setSeats(ticketType.getSeats().stream()
                .filter((ticketTier -> ticketTier.getDeletedAt() == null))
                .map((seatMapper::toDTO))
                .collect(Collectors.toList()));
        return ticketTypeDTO;
    }

    public TicketTypeInfoDTO toInfoDTO(TicketType ticketType) {
        TicketTypeInfoDTO ticketTypeDto = modelMapper.map(ticketType, TicketTypeInfoDTO.class);
        LocalDateTime now = LocalDateTime.now();

        //Lấy danh sách hợp lệ (không xóa, không inactive)
        List<TicketTier> validTiers = ticketType.getTicketTiers().stream()
                .filter(tt -> tt.getDeletedAt() == null && tt.getStatus() != TicketTierStatus.INACTIVE)
                .sorted(Comparator.comparing(TicketTier::getSaleStartTime))
                .collect(Collectors.toList());

        TicketTier activeTier = validTiers.stream()
                .filter(tt -> !now.isBefore(tt.getSaleStartTime()) && now.isBefore(tt.getSaleEndTime()))
                .findFirst()
                .orElse(null);

        // Nếu không có tier nào trong khung giờ, lấy cái cao giá nhất
        if (activeTier == null) {
            activeTier = validTiers.stream()
                    .max(Comparator.comparing(TicketTier::getPrice))
                    .orElse(null);
        }

        if (activeTier != null) {
            ticketTypeDto.setTierId(activeTier.getId());
            ticketTypeDto.setTierName(activeTier.getName());
            ticketTypeDto.setTierPrice(activeTier.getPrice());
            ticketTypeDto.setTierStatus(activeTier.getStatus());
            ticketTypeDto.setTierDescription(activeTier.getDescription());
        }

        //Tính Status dựa trên chính thông tin Tier đã chọn và danh sách validTiers
        ticketTypeDto.setStatus(calculateStatus(ticketType, activeTier, validTiers, now));
        return ticketTypeDto;
    }

    public TicketTypeSummaryDTO toSummaryDTO(TicketType ticketType) {
        TicketTypeSummaryDTO ticketTypeSummaryDTO = modelMapper.map(ticketType, TicketTypeSummaryDTO.class);
        List<TicketTierSummaryDTO> tierSummaryDTOS = ticketType.getTicketTiers()
                .stream().filter(tier -> tier.getDeletedAt() == null)
                .map(ticketTierMapper::toSummaryDTO)
                .collect(Collectors.toList());
        ticketTypeSummaryDTO.setTicketTiers(tierSummaryDTOS);
        ticketTypeSummaryDTO.setAdminStatus(ticketType.getStatus());
        ticketTypeSummaryDTO.setBusinessStatus(calculateSingleTierBusinessStatus(ticketType));
        ticketTypeSummaryDTO.setTotalRevenue(tierSummaryDTOS.stream()
                .mapToLong(TicketTierSummaryDTO::getTotalRevenue)
                .sum());
        return ticketTypeSummaryDTO;
    }

    private TicketTypeStatus calculateStatus(TicketType ticketType, TicketTier activeTier, List<TicketTier> allTiers, LocalDateTime now) {
        if (ticketType.getStatus() == TicketTypeStatus.SUSPENDED) return TicketTypeStatus.SUSPENDED;
        if (activeTier == null) return TicketTypeStatus.EXPIRED;
        // Kiểm tra loại vé đó còn hàng không
        if (ticketType.getSoldQuantity() >= ticketType.getTotalQuantity()) return TicketTypeStatus.SOLD_OUT;
        // Nếu Tier được chọn đang trong thời gian bán
        if (!now.isBefore(activeTier.getSaleStartTime()) && now.isBefore(activeTier.getSaleEndTime())) {
            if (activeTier.getStatus() == TicketTierStatus.SUSPENDED) return TicketTypeStatus.SUSPENDED;
            // Check hết vé
            if (activeTier.getReservedQuantity() >= activeTier.getLimitQuantity()) {
                boolean hasFutureTier = allTiers.stream().anyMatch(t -> t.getSaleStartTime().isAfter(now));
                return hasFutureTier ? TicketTypeStatus.TIER_SOLD_OUT : TicketTypeStatus.SOLD_OUT;
            }
            return TicketTypeStatus.ON_SALE;
        }
        // Nếu không trong khung giờ bán: Kiểm tra xem là chưa tới hay đã qua
        if (activeTier.getSaleStartTime().isAfter(now)) {
            return TicketTypeStatus.COMING_SOON;
        }
        return TicketTypeStatus.EXPIRED;
    }

    private TicketTypeStatus calculateSingleTierBusinessStatus(TicketType ticketType) {
        LocalDateTime now = LocalDateTime.now();
        List<TicketTier> tiers = ticketType.getTicketTiers().stream()
                .filter(t -> t.getDeletedAt() == null && t.getStatus() != TicketTierStatus.INACTIVE)
                .sorted(Comparator.comparing(TicketTier::getSaleStartTime))
                .collect(Collectors.toList());

        // 1. Tìm Tier "Lẽ ra phải đang bán" dựa trên thời gian
        Optional<TicketTier> currentTier = tiers.stream()
                .filter(t -> !now.isBefore(t.getSaleStartTime()) && now.isBefore(t.getSaleEndTime()))
                .findFirst();

        if (currentTier.isPresent()) {
            TicketTier tier = currentTier.get();

            // Nếu tạm dừng Tier này
            if (tier.getStatus() == TicketTierStatus.SUSPENDED) return TicketTypeStatus.SUSPENDED;

            // Nếu Tier này hết vé
            if (tier.getReservedQuantity() >= tier.getLimitQuantity()) {
                boolean hasFutureTier = tiers.stream().anyMatch(t -> t.getSaleStartTime().isAfter(now));
                return hasFutureTier ? TicketTypeStatus.COMING_SOON : TicketTypeStatus.SOLD_OUT;
            }

            return TicketTypeStatus.ON_SALE;
        }

        // Kiểm tra xem có cái nào sắp tới không
        boolean isAnyUpcoming = tiers.stream().anyMatch(t -> t.getSaleStartTime().isAfter(now));
        if (isAnyUpcoming) return TicketTypeStatus.COMING_SOON;

        // Nếu tất cả đã qua khung giờ
        return TicketTypeStatus.EXPIRED;
    }
}
