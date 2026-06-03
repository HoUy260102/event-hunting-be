package com.example.event.dto.response;

import lombok.*;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerAnalyticsResponse {
    private List<TopCustomerResponse> customers;
    private Long totalUniqueCustomers;
    private Double retentionRate;
}
