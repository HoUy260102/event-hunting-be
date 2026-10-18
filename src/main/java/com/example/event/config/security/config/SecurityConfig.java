package com.example.event.config.security.config;

import com.example.event.config.security.jwt.JwtAccessDeniedHandler;
import com.example.event.config.security.jwt.JwtAuthEntryPoint;
import com.example.event.config.security.jwt.JwtTokenFilter;
import com.example.event.config.security.user.CustomUserDetailsService;
import com.example.event.filter.UserRateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final JwtTokenFilter jwtTokenFilter;
    private final UserRateLimit userRateLimit;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());
        daoAuthenticationProvider.setUserDetailsService(userDetailsService);
        return daoAuthenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .headers(headers -> headers
                        .addHeaderWriter(
                                new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin-allow-popups")))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(
                        exception -> exception
                                .authenticationEntryPoint(jwtAuthEntryPoint)
                                .accessDeniedHandler(jwtAccessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> {
                    requests
                            .requestMatchers(HttpMethod.GET, "/comments/event/*", "/comments/*/replies").permitAll()
                            .requestMatchers(
                                    "/payments/vnpay-callback",
                                    "/categories",
                                    "/provinces",
                                    "/events/public/search",
                                    "/events/trending",
                                    "/events/recommendations",
                                    "/events/*/info",
                                    "/auth/login",
                                    "/auth/refresh-token",
                                    "/auth/resend-verify",
                                    "/auth/signup",
                                    "/auth/forgot-password/**",
                                    "/auth/callback/google",
                                    "/auth/google/url",
                                    "/auth/verify",
                                    "/ws/**",
                                    "/topic/**",
                                    "/app/**")
                            .permitAll()
                            .anyRequest().authenticated();
                });
        http.authenticationProvider(daoAuthenticationProvider());
        http.addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(userRateLimit, JwtTokenFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Cho phép các domain nào được gọi
        List<String> origins = new ArrayList<>(Arrays.asList("http://localhost:5173", "http://localhost:3000"));
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            String sanitized = frontendUrl.replaceAll("/+$", "");
            if (!origins.contains(sanitized)) {
                origins.add(sanitized);
            }
        }
        configuration.setAllowedOriginPatterns(origins);

        // Cho phép các method nào
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Cho phép các header nào (thường là Authorization, Content-Type,...)
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Cho phép gửi credentials (cookie, authorization header)
        configuration.setAllowCredentials(true);

        // Expose headers cho phía client (FE) đọc được requestId
        configuration.setExposedHeaders(Arrays.asList("X-Request-Id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
