package com.example.event.config.security;

import com.example.event.config.security.user.CustomUserDetails;
import com.example.event.entity.Role;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {
    public String getCurrentUserId() {
        CustomUserDetails user =
                (CustomUserDetails) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        return user.getUser().getId();
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
