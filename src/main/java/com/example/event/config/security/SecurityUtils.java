package com.example.event.config.security;

import com.example.event.config.security.user.CustomUserDetails;
import com.example.event.entity.Role;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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

    public boolean canAccessThisResource(String userId) {
        CustomUserDetails user =
                (CustomUserDetails) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();
        Role role = user.getUser().getRole();
        return role.getName().equals("ADMIN") || user.getUser().getId().equals(userId);
    }
}
