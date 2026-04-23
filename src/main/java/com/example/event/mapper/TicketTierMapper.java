package com.example.event.mapper;

import com.example.event.constant.TicketTierStatus;
import com.example.event.dto.TicketTierDTO;
import com.example.event.entity.TicketTier;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TicketTierMapper {
    private final ModelMapper modelMapper;

    public TicketTierDTO toDTO(TicketTier ticketTier) {
        TicketTierDTO ticketTierDTO = modelMapper.map(ticketTier, TicketTierDTO.class);
        return ticketTierDTO;
    }

    private TicketTierStatus calculateBusinessTierStatus(TicketTier tier) {
        LocalDateTime now = LocalDateTime.now();

        // Kiểm tra hết hạn trước
        if (tier.getSaleEndTime() != null && now.isAfter(tier.getSaleEndTime())) {
            return TicketTierStatus.EXPIRED;
        }

        // Kiểm tra hết vé (nếu còn trong hạn nhưng số lượng = 0)
        if (tier.getLimitQuantity() - tier.getReservedQuantity() <= 0) {
            return TicketTierStatus.SOLD_OUT;
        }

        // Kiểm tra chưa đến giờ bán
        if (tier.getSaleStartTime() != null && now.isBefore(tier.getSaleStartTime())) {
            return TicketTierStatus.COMING_SOON;
        }

        // Nếu thỏa mãn: now >= start AND now < end AND quantity > 0
        return TicketTierStatus.ON_SALE;
    }
}
