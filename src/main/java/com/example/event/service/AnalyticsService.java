package com.example.event.service;

import com.example.event.dto.response.AnalyticsOverviewResponse;
import com.example.event.dto.response.RevenueChartPointResponse;
import com.example.event.dto.response.TopCustomerResponse;
import com.example.event.dto.response.CustomerAnalyticsResponse;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsService {
    AnalyticsOverviewResponse getOverview(LocalDate start, LocalDate end);
    List<RevenueChartPointResponse> getRevenueChart(LocalDate start, LocalDate end, String type);
    CustomerAnalyticsResponse getCustomerAnalytics(LocalDate start, LocalDate end, Integer limit);
}
