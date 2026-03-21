package com.example.event.config.security.jwt;

import com.example.event.config.security.user.CustomUserDetails;
import com.example.event.config.security.user.CustomUserDetailsService;
import com.example.event.constant.ErrorCode;
import com.example.event.dto.response.ErrorResponse;
import com.example.event.exception.JwtAuthenticationException;
import com.example.event.service.SessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import javax.security.sasl.AuthenticationException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {
    private final CustomUserDetailsService customUserDetailsService;
    private final SessionService sessionService;
    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (isByPassToken(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                final String token = authHeader.substring(7).trim();
                final Claims claims = jwtUtils.extractAllClaims(token);
                final String username = claims.getSubject();
                final String sid = (String) claims.get("sid");
                final String type = claims.get("type", String.class);
                if (!type.equalsIgnoreCase("access")) {
                    throw new AccessDeniedException(ErrorCode.TOKEN_TYPE_INVALID.getMessage());
                }
                if (sessionService.isBlackList(sid)) {
                    throw new JwtAuthenticationException(ErrorCode.TOKEN_INVALID);
                }
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    CustomUserDetails customUserDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(username);
                    if (jwtUtils.validateToken(token)) {
                        UsernamePasswordAuthenticationToken authenticationToken =
                                new UsernamePasswordAuthenticationToken(customUserDetails, null,
                                        customUserDetails.getAuthorities());
                        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    }
                }
            }
        } catch (JwtAuthenticationException e) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code(e.getErrorCode().name())
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .message(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(errorResponse);
            response.getWriter().write(json);
            return;
        } catch (AuthenticationException | AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code(HttpStatus.INTERNAL_SERVER_ERROR.name())
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .message(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(errorResponse);
            response.getWriter().write(json);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isByPassToken(HttpServletRequest request) {
        String path = request.getServletPath();
        AntPathMatcher pathMatcher = new AntPathMatcher();

        if (path.startsWith("/ws")
                || path.startsWith("/topic")
                || path.startsWith("/app")) {
            return true;
        }

        List<Pair<String, String>> bypassTokens = new ArrayList<>(Arrays.asList(
                Pair.of("/auth/login", "POST"),
                Pair.of("/auth/refresh-token", "GET"),
                Pair.of("/auth/signup", "POST"),
                Pair.of("/auth/verify", "GET"),
                Pair.of("/auth/resend-verify", "POST"),

                Pair.of("/categories", "GET"),
                Pair.of("/events/public/search", "GET"),
                Pair.of("/events/*/info", "GET"),
                Pair.of("/payments/vnpay-callback", "GET")
        ));

        return bypassTokens.stream().anyMatch(
                pair -> pair.getSecond().equals(request.getMethod())
                        && pathMatcher.match(pair.getFirst(), path)
        );
    }
}
