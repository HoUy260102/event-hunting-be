package com.example.event.mapper;

import com.example.event.dto.VoucherDTO;
import com.example.event.entity.Voucher;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VoucherMapper {
    private final ModelMapper modelMapper;

    public VoucherDTO toDTO(Voucher voucher) {
        VoucherDTO voucherDTO = modelMapper.map(voucher, VoucherDTO.class);
        voucherDTO.setShowId(voucher.getShow() != null ? voucher.getShow().getId() : null);
        voucherDTO.setTicketTypeIds((voucher.getTicketTypes() != null && !voucher.getTicketTypes().isEmpty()) ? voucher.getTicketTypes()
                .stream().map(ticketType -> ticketType.getId())
                .collect(Collectors.toList()) : Collections.emptyList());
        return voucherDTO;
    }
}
