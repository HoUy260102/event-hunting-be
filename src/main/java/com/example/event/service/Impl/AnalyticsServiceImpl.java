package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.dto.response.AnalyticsOverviewResponse;
import com.example.event.dto.response.AnalyticsOverviewProjection;
import com.example.event.dto.response.RevenueChartPointResponse;
import com.example.event.dto.response.TopEventResponse;
import com.example.event.dto.response.TopEventProjection;
import com.example.event.dto.response.TopShowResponse;
import com.example.event.dto.response.TopShowProjection;
import com.example.event.dto.response.TicketTierDistributionResponse;
import com.example.event.dto.response.TicketTierDistributionProjection;
import com.example.event.dto.response.TopCustomerResponse;
import com.example.event.dto.response.TopCustomerProjection;
import com.example.event.dto.response.CustomerAnalyticsResponse;
import org.springframework.data.domain.PageRequest;
import java.util.stream.Collectors;
import com.example.event.entity.Reservation;
import com.example.event.entity.ReservationItem;
import com.example.event.repository.EventRepository;
import com.example.event.repository.ReservationRepository;
import com.example.event.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    private final SecurityUtils securityUtils;

    @Override
    public AnalyticsOverviewResponse getOverview(LocalDate start, LocalDate end) {
        if (start == null) start = LocalDate.now().minusDays(30);
        if (end == null) end = LocalDate.now();

        String userId = securityUtils.getCurrentUserId();
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        // 1. Lấy dữ liệu thống kê số liệu tổng quan
        AnalyticsOverviewProjection proj = reservationRepository.getOverviewProjectionByUserId(userId, startDateTime, endDateTime);

        // 2. Lấy dữ liệu Top 5 sự kiện doanh thu cao nhất kèm show bán chạy nhất
        List<TopEventProjection> topEventsProj = eventRepository.findTopEventsByUserIdAndDateRange(userId, startDateTime, endDateTime, PageRequest.of(0, 5));
        
        List<TopEventResponse> topEvents = topEventsProj.stream().map(e -> {
            List<TopShowProjection> showsProj = reservationRepository.findTopShowsByEventIdAndDateRange(e.getId(), startDateTime, endDateTime);
            List<TopShowResponse> topShows = showsProj.stream().map(s -> 
                TopShowResponse.builder()
                        .showId(s.getShowId())
                        .startTime(s.getStartTime() != null ? s.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy")) : "N/A")
                        .ticketsSold(s.getTicketsSold() != null ? s.getTicketsSold() : 0L)
                        .revenue(s.getRevenue() != null ? s.getRevenue() : 0L)
                        .build()
            ).collect(Collectors.toList());

            return TopEventResponse.builder()
                    .id(e.getId())
                    .name(e.getName())
                    .revenue(e.getRevenue() != null ? e.getRevenue() : 0L)
                    .ticketsSold(e.getTicketsSold() != null ? e.getTicketsSold() : 0L)
                    .topShows(topShows)
                    .build();
        }).collect(Collectors.toList());

        return AnalyticsOverviewResponse.builder()
                .totalRevenue(proj != null && proj.getTotalRevenue() != null ? proj.getTotalRevenue() : 0L)
                .totalTicketsSold(proj != null && proj.getTotalTicketsSold() != null ? proj.getTotalTicketsSold() : 0L)
                .totalBookings(proj != null && proj.getTotalBookings() != null ? proj.getTotalBookings() : 0L)
                .totalEventsCreated(proj != null && proj.getTotalEventsCreated() != null ? proj.getTotalEventsCreated() : 0L)
                .topEvents(topEvents)
                .build();
    }

    @Override
    public List<RevenueChartPointResponse> getRevenueChart(LocalDate start, LocalDate end, String type) {
        if (start == null) start = LocalDate.now().minusDays(30);
        if (end == null) end = LocalDate.now();

        String userId = securityUtils.getCurrentUserId();
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        List<Reservation> reservations = reservationRepository.findPaidReservationsByUserIdAndDateRange(userId, startDateTime, endDateTime);

        List<RevenueChartPointResponse> chartPoints = new ArrayList<>();
        String upperType = type != null ? type.toUpperCase() : "DAY";

        switch (upperType) {
            case "WEEK":
                // Gom nhóm theo TUẦN
                DateTimeFormatter weekFormatter = DateTimeFormatter.ofPattern("dd/MM");
                LocalDate currentWeek = start;
                while (!currentWeek.isAfter(end)) {
                    LocalDate weekEnd = currentWeek.plusDays(6).isAfter(end) ? end : currentWeek.plusDays(6);
                    String label = currentWeek.format(weekFormatter) + " - " + weekEnd.format(weekFormatter);

                    long revenueSum = 0L;
                    long ticketsSum = 0L;

                    for (Reservation r : reservations) {
                        LocalDate resDate = r.getCreatedAt().toLocalDate();
                        if (!resDate.isBefore(currentWeek) && !resDate.isAfter(weekEnd)) {
                            revenueSum += r.getFinalAmount() != null ? r.getFinalAmount() : 0L;
                            if (r.getItems() != null) {
                                    for (ReservationItem item : r.getItems()) {
                                        ticketsSum += item.getQuantity() != null ? item.getQuantity() : 0;
                                    }
                            }
                        }
                    }

                    chartPoints.add(new RevenueChartPointResponse(label, revenueSum, ticketsSum));
                    currentWeek = currentWeek.plusDays(7);
                }
                break;

            case "MONTH":
                // Gom nhóm theo THÁNG
                DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MM/yyyy");
                LocalDate currentMonth = start.withDayOfMonth(1);
                LocalDate finalEndMonth = end.withDayOfMonth(1);
                while (!currentMonth.isAfter(finalEndMonth)) {
                    int month = currentMonth.getMonthValue();
                    int year = currentMonth.getYear();
                    String label = "T" + currentMonth.format(monthFormatter);

                    long revenueSum = 0L;
                    long ticketsSum = 0L;

                    for (Reservation r : reservations) {
                        LocalDateTime resTime = r.getCreatedAt();
                        if (resTime.getMonthValue() == month && resTime.getYear() == year) {
                            revenueSum += r.getFinalAmount() != null ? r.getFinalAmount() : 0L;
                            if (r.getItems() != null) {
                                for (ReservationItem item : r.getItems()) {
                                    ticketsSum += item.getQuantity() != null ? item.getQuantity() : 0;
                                }
                            }
                        }
                    }

                    chartPoints.add(new RevenueChartPointResponse(label, revenueSum, ticketsSum));
                    currentMonth = currentMonth.plusMonths(1);
                }
                break;

            case "YEAR":
                // Gom nhóm theo NĂM
                LocalDate currentYear = start.withDayOfYear(1);
                LocalDate finalEndYear = end.withDayOfYear(1);
                while (!currentYear.isAfter(finalEndYear)) {
                    int year = currentYear.getYear();
                    String label = "Năm " + year;

                    long revenueSum = 0L;
                    long ticketsSum = 0L;

                    for (Reservation r : reservations) {
                        LocalDateTime resTime = r.getCreatedAt();
                        if (resTime.getYear() == year) {
                            revenueSum += r.getFinalAmount() != null ? r.getFinalAmount() : 0L;
                            if (r.getItems() != null) {
                                for (ReservationItem item : r.getItems()) {
                                    ticketsSum += item.getQuantity() != null ? item.getQuantity() : 0;
                                }
                            }
                        }
                    }

                    chartPoints.add(new RevenueChartPointResponse(label, revenueSum, ticketsSum));
                    currentYear = currentYear.plusYears(1);
                }
                break;

            case "DAY":
            default:
                // Gom nhóm theo NGÀY
                DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");
                LocalDate currentDay = start;
                while (!currentDay.isAfter(end)) {
                    LocalDate finalCurrent = currentDay;
                    String label = currentDay.format(dayFormatter);

                    long revenueSum = 0L;
                    long ticketsSum = 0L;

                    for (Reservation r : reservations) {
                        LocalDate resDate = r.getCreatedAt().toLocalDate();
                        if (resDate.equals(finalCurrent)) {
                            revenueSum += r.getFinalAmount() != null ? r.getFinalAmount() : 0L;
                            if (r.getItems() != null) {
                                for (ReservationItem item : r.getItems()) {
                                    ticketsSum += item.getQuantity() != null ? item.getQuantity() : 0;
                                }
                            }
                        }
                    }

                    chartPoints.add(new RevenueChartPointResponse(label, revenueSum, ticketsSum));
                    currentDay = currentDay.plusDays(1);
                }
                break;
        }

        return chartPoints;
    }

    @Override
    public CustomerAnalyticsResponse getCustomerAnalytics(LocalDate start, LocalDate end, Integer limit) {
        if (start == null) start = LocalDate.now().minusDays(30);
        if (end == null) end = LocalDate.now();
        if (limit == null || limit <= 0) limit = 10;

        String userId = securityUtils.getCurrentUserId();
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        // 1. Get Top Customers list
        List<TopCustomerProjection> projections = reservationRepository.findTopCustomersByOrganizerIdAndDateRange(
                userId, startDateTime, endDateTime, PageRequest.of(0, limit)
        );

        List<TopCustomerResponse> topCustomers = projections.stream().map(p -> TopCustomerResponse.builder()
                .userId(p.getUserId())
                .name(p.getName())
                .email(p.getEmail())
                .avatarUrl(p.getAvatarUrl())
                .totalBookings(p.getTotalBookings() != null ? p.getTotalBookings() : 0L)
                .totalSpent(p.getTotalSpent() != null ? p.getTotalSpent() : 0L)
                .totalTickets(p.getTotalTickets() != null ? p.getTotalTickets() : 0L)
                .build()
        ).collect(Collectors.toList());

        // 2. Calculate Repeat Purchase/Customer Retention Rate
        List<Long> orderCountsPerUser = reservationRepository.getOrderCountsPerUser(userId, startDateTime, endDateTime);
        long totalUniqueCustomers = orderCountsPerUser.size();
        long repeatCustomers = orderCountsPerUser.stream().filter(count -> count >= 2).count();
        double retentionRate = totalUniqueCustomers > 0 
                ? Math.round(((double) repeatCustomers * 100.0 / totalUniqueCustomers) * 10.0) / 10.0 
                : 0.0;

        return CustomerAnalyticsResponse.builder()
                .customers(topCustomers)
                .totalUniqueCustomers(totalUniqueCustomers)
                .retentionRate(retentionRate)
                .build();
    }
}
