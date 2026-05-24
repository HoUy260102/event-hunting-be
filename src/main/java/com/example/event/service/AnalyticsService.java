package com.example.event.service;

import com.example.event.dto.response.AnalyticsOverviewResponse;
import com.example.event.dto.response.RevenueChartPointResponse;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsService {
    AnalyticsOverviewResponse getOverview(LocalDate start, LocalDate end);
    List<RevenueChartPointResponse> getRevenueChart(LocalDate start, LocalDate end, String type);
}
