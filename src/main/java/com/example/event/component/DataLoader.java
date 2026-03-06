package com.example.event.component;

import com.example.event.constant.RoleStatus;
import com.example.event.entity.Role;
import com.example.event.entity.User;
import com.example.event.repository.RoleRepository;
import com.example.event.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DataLoader {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadData() {

        Role adminRole = Optional.ofNullable(roleRepository.findByName("ADMIN")).orElseGet(() -> {
            Role newRole = new Role();
            newRole.setName("ADMIN");
            newRole.setStatus(RoleStatus.ACTIVE);
            newRole.setUpdatedAt(LocalDateTime.now());
            newRole.setCreatedAt(LocalDateTime.now());
            roleRepository.save(newRole);
            return newRole;
        });

        Role userRole = Optional.ofNullable(roleRepository.findByName("USER")).orElseGet(() -> {
            Role newRole = new Role();
            newRole.setName("USER");
            newRole.setStatus(RoleStatus.ACTIVE);
            newRole.setUpdatedAt(LocalDateTime.now());
            newRole.setCreatedAt(LocalDateTime.now());
            roleRepository.save(newRole);
            return newRole;
        });

        if (userRepository.existsUserByEmail("Admin1@gmail.com") == false) {
            User user = new User();
            user.setEmail("Admin1@gmail.com");
            user.setPassword(passwordEncoder.encode("123456"));
            user.setRole(adminRole);
            user.setVerified(true);
            user.setUpdatedAt(LocalDateTime.now());
            user.setCreatedAt(LocalDateTime.now());
            userRepository.save(user);
        }

    }
}
