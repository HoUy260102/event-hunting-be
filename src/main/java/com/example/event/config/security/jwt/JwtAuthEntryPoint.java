package com.example.event.config.security.jwt;

import com.example.event.exception.JwtAuthenticationException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        int status = 401;
        String code = "UNAUTHENTICATED";
        if (authException instanceof JwtAuthenticationException) {
            JwtAuthenticationException jwtAuthExcep = (JwtAuthenticationException) authException;
            status = jwtAuthExcep.getErrorCode().getHttpStatus().value();
            code = jwtAuthExcep.getErrorCode().name();
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        final Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("message", authException.getMessage());
        body.put("code", code);
        final ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), body);
    }
}
