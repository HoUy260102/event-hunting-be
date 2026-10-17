package com.example.event.config;

import com.example.event.filter.MdcFilter;
import com.example.event.filter.RateLimit;
import com.example.event.filter.UserRateLimit;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<MdcFilter> mdcFilterRegistration(MdcFilter filter) {
        FilterRegistrationBean<MdcFilter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<RateLimit> rateLimitFilter(RateLimit filter) {
        FilterRegistrationBean<RateLimit> bean = new FilterRegistrationBean<>();

        bean.setFilter(filter);
        bean.addUrlPatterns("/*");
        bean.setOrder(1);

        return bean;
    }

    @Bean
    public FilterRegistrationBean<UserRateLimit> userRateLimitFilterRegistration(UserRateLimit filter) {
        FilterRegistrationBean<UserRateLimit> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }
}

