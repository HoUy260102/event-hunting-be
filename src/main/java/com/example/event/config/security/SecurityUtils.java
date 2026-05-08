package com.example.event.config.security;

import com.example.event.config.security.user.CustomUserDetails;
import com.example.event.constant.ErrorCode;
import com.example.event.exception.AppException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class SecurityUtils {
    public String getCurrentUserId() {
        var context = SecurityContextHolder.getContext();
        if (context == null || context.getAuthentication() == null) {
            return null;
        }
        Object principal = context.getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails user) {
            return user.getUser().getId();
        }
        return null;
    }

    public void canAccessThisResource(String resourceOwnerId) {
        List<String> allows = new ArrayList<>(Arrays.asList("ROLE_ADMIN"));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
        String currentUserId = user.getUser().getId();
        boolean isAllow = user.getAuthorities().stream()
                .anyMatch(a -> allows.contains(a.getAuthority()));
        if (!isAllow && !resourceOwnerId.equals(currentUserId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
