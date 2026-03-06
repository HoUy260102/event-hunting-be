package com.example.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailPropertiesConfig {
    @Bean
    @ConfigurationProperties(prefix = "spring.mail")
    public MailProperties mailProperties() {
        return new MailProperties();
    }
}
