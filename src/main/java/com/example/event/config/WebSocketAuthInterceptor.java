package com.example.event.config;

import com.example.event.config.security.jwt.JwtUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.exception.JwtAuthenticationException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private final JwtUtils jwtUtils;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor
                .getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (!"null".equals(token)) {
                    String userId = jwtUtils.getUserIdFromToken(token);
                    accessor.setUser(
                            new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>())
                    );
                    System.out.println("WS PRINCIPAL SET: [" + userId + "]");
                }
            }
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                || StompCommand.SEND.equals(accessor.getCommand())) {

            String destination = accessor.getDestination();

            if (destination != null && destination.startsWith("/p/")) {
                if (accessor.getUser() == null) {
                    throw new JwtAuthenticationException(ErrorCode.TOKEN_EXPIRED);
                }
            }
        }

        return message;
    }}