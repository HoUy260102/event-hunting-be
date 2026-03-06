package com.example.event.config;

import com.example.event.filter.RateLimit;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RateLimit> rateLimitFilter(RateLimit filter) {
        FilterRegistrationBean<RateLimit> bean = new FilterRegistrationBean<>();

        bean.setFilter(filter);
        bean.addUrlPatterns("/auth/login");
        bean.addUrlPatterns("/auth/resend-verify");
        bean.setOrder(1);

        return bean;
    }
}

