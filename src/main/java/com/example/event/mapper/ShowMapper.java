package com.example.event.mapper;

import com.example.event.constant.SeatMapType;
import com.example.event.constant.SeatingType;
import com.example.event.constant.ShowStatus;
import com.example.event.constant.TicketTypeStatus;
import com.example.event.dto.*;
import com.example.event.entity.Show;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ShowMapper {
    private final ModelMapper modelMapper;
    private final TicketTypeMapper ticketTypeMapper;

    public ShowDTO toDTO(Show show) {
        ShowDTO showDTO = modelMapper.map(show, ShowDTO.class);
        List<TicketTypeDTO> ticketTypeDTOS = show.getTicketTypes().stream()
                .filter((ticketType) -> ticketType.getDeletedAt() == null)
                .map(ticketType -> {
                    TicketTypeDTO ticketTypeDTO = ticketTypeMapper.toDTO(ticketType);
                    ticketTypeDTO.setSeats(
                            showDTO.getSeatMapType() == SeatMapType.SECTION_WITH_SEATS
                                    && ticketType.getSeatingType() == SeatingType.SEATED ? ticketTypeDTO.getSeats()
                                    : new ArrayList<>()
                    );
                    return ticketTypeDTO;
                })
                .collect(Collectors.toList());
        showDTO.setTicketTypes(ticketTypeDTOS);
        return showDTO;
    }

    public ShowInfoDTO toInfoDTO(Show show) {
        ShowInfoDTO showInfoDTO = modelMapper.map(show, ShowInfoDTO.class);
        List<TicketTypeInfoDTO> ticketTypeInfoDTOS = show.getTicketTypes().stream()
                .filter(ticketType -> ticketType.getDeletedAt() == null && ticketType.getStatus() != TicketTypeStatus.INACTIVE)
                .map(ticketTypeMapper::toInfoDTO)
                .collect(Collectors.toList());
        showInfoDTO.setTicketTypes(ticketTypeInfoDTOS);
        showInfoDTO.setStatus(calculateShowStatusFromInfo(show, ticketTypeInfoDTOS));
        return showInfoDTO;
    }

    public ShowBookingDTO toBookingDTO(Show show) {
        ShowBookingDTO showBookingDTO = modelMapper.map(show, ShowBookingDTO.class);
        List<TicketTypeBookingDTO> ticketTypeBookingDTOS = show.getTicketTypes().stream()
                .filter(ticketType -> ticketType.getDeletedAt() == null && ticketType.getStatus() != TicketTypeStatus.INACTIVE)
                .map(ticketTypeMapper::toBookingDTO)
                .collect(Collectors.toList());
        showBookingDTO.setTicketTypes(ticketTypeBookingDTOS);
        showBookingDTO.setStatus(calculateShowStatusFromBooking(show, ticketTypeBookingDTOS));
        return showBookingDTO;
    }

    public ShowSummaryDTO toSummaryDTO(Show show) {
        ShowSummaryDTO showSummaryDTO = ShowSummaryDTO.builder()
                .id(show.getId())
                .startTime(show.getStartTime())
                .endTime(show.getEndTime())
                .build();
        List<TicketTypeSummaryDTO> typeSummaryDTOS = show.getTicketTypes()
                .stream()
                .filter(ticketType -> ticketType.getDeletedAt() == null &&
                        (ticketType.getStatus() == TicketTypeStatus.ACTIVE || ticketType.getStatus() == TicketTypeStatus.SUSPENDED))
                .map(ticketTypeMapper::toSummaryDTO)
                .collect(Collectors.toList());
        LocalDateTime startTime = show.getStartTime();
        showSummaryDTO.setTicketTypes(typeSummaryDTOS);
        showSummaryDTO.setTotalQuantity(typeSummaryDTOS.stream()
                .mapToInt(TicketTypeSummaryDTO::getTotalQuantity)
                .sum());
        showSummaryDTO.setSoldQuantity(typeSummaryDTOS.stream()
                .mapToInt(TicketTypeSummaryDTO::getSoldQuantity)
                .sum());
        showSummaryDTO.setTotalRevenue(typeSummaryDTOS.stream()
                .mapToLong(TicketTypeSummaryDTO::getTotalRevenue)
                .sum());
        showSummaryDTO.setStatus(calculateShowSummaryStatus(show, typeSummaryDTOS));
        showSummaryDTO.setStartDay(String.valueOf(startTime.getDayOfMonth()));
        showSummaryDTO.setStartMonth(String.valueOf(startTime.getMonth()));
        return showSummaryDTO;
    }

    private ShowStatus calculateShowSummaryStatus(Show show, List<TicketTypeSummaryDTO> ticketTypes) {
        LocalDateTime now = LocalDateTime.now();
        if (show.getDeletedAt() != null) return ShowStatus.DELETED;
        if (show.getStatus() == ShowStatus.CANCELLED) return ShowStatus.CANCELLED;
        if (show.getStatus() == ShowStatus.POSTPONED) return ShowStatus.POSTPONED;
        if (now.isAfter(show.getEndTime())) return ShowStatus.FINISHED;
        // Nếu tất cả các TicketType đều có trạng thái SOLD_OUT
        boolean isAllSoldOut = ticketTypes.stream()
                .filter(type -> type.getAdminStatus() == TicketTypeStatus.ACTIVE)
                .allMatch(type -> type.getSoldQuantity() >= type.getTotalQuantity());
        if (isAllSoldOut) {
            return ShowStatus.SOLD_OUT;
        }
        // Nếu chưa hết vé thì quay về tính theo thời gian
        if (now.isBefore(show.getStartTime())) {
            return ShowStatus.UPCOMING;
        }
        return ShowStatus.HAPPENING;
    }

    private ShowStatus calculateShowStatusFromInfo(Show show, List<TicketTypeInfoDTO> ticketTypes) {
        LocalDateTime now = LocalDateTime.now();
        if (show.getDeletedAt() != null) return ShowStatus.DELETED;
        if (show.getStatus() == ShowStatus.CANCELLED) return ShowStatus.CANCELLED;
        if (show.getStatus() == ShowStatus.POSTPONED) return ShowStatus.POSTPONED;
        if (now.isAfter(show.getEndTime())) return ShowStatus.FINISHED;
        // Nếu tất cả các TicketType đều có trạng thái SOLD_OUT
        boolean isAllSoldOut = ticketTypes.stream()
                .allMatch(type -> type.getStatus() == TicketTypeStatus.SOLD_OUT);
        if (isAllSoldOut) {
            return ShowStatus.SOLD_OUT;
        }
        boolean isAnyOnSale = ticketTypes.stream()
                .anyMatch(type -> type.getStatus() == TicketTypeStatus.ON_SALE);
        if (isAnyOnSale) {
            return ShowStatus.ON_SALE;
        }
        // Nếu chưa hết vé thì quay về tính theo thời gian
        if (!now.isBefore(show.getStartTime()) && now.isBefore(show.getEndTime())) {
            return ShowStatus.HAPPENING;
        }
        return ShowStatus.UPCOMING;
    }

    private ShowStatus calculateShowStatusFromBooking(Show show, List<TicketTypeBookingDTO> ticketTypes) {
        LocalDateTime now = LocalDateTime.now();
        if (show.getDeletedAt() != null) return ShowStatus.DELETED;
        if (show.getStatus() == ShowStatus.CANCELLED) return ShowStatus.CANCELLED;
        if (show.getStatus() == ShowStatus.POSTPONED) return ShowStatus.POSTPONED;
        if (now.isAfter(show.getEndTime())) return ShowStatus.FINISHED;
        // Nếu tất cả các TicketType đều có trạng thái SOLD_OUT
        boolean isAllSoldOut = ticketTypes.stream()
                .allMatch(type -> type.getStatus() == TicketTypeStatus.SOLD_OUT);
        if (isAllSoldOut) {
            return ShowStatus.SOLD_OUT;
        }
        boolean isAnyOnSale = ticketTypes.stream()
                .anyMatch(type -> type.getStatus() == TicketTypeStatus.ON_SALE);
        if (isAnyOnSale) {
            return ShowStatus.ON_SALE;
        }
        // Nếu chưa hết vé thì quay về tính theo thời gian
        if (!now.isBefore(show.getStartTime()) && now.isBefore(show.getEndTime())) {
            return ShowStatus.HAPPENING;
        }
        return ShowStatus.UPCOMING;
    }
}
