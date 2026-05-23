package com.example.event.component;

import com.example.event.constant.RoleStatus;
import com.example.event.entity.Permission;
import com.example.event.entity.Role;
import com.example.event.entity.User;
import com.example.event.repository.PermissionRepository;
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
    private final PermissionRepository permissionRepository;
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

        // Seed Permissions
        String[][] permissions = {
                {"USER:VIEW", "Xem danh sách người dùng", "USER"},
                {"USER:CREATE", "Tạo người dùng", "USER"},
                {"USER:UPDATE", "Cập nhật người dùng", "USER"},
                {"USER:DELETE", "Xóa người dùng", "USER"},
                {"USER:RESTORE", "Khôi phục người dùng", "USER"},

                {"ROLE:VIEW", "Xem danh sách vai trò", "ROLE"},
                {"ROLE:CREATE", "Tạo vai trò", "ROLE"},
                {"ROLE:UPDATE", "Cập nhật vai trò", "ROLE"},
                {"ROLE:DELETE", "Xóa vai trò", "ROLE"},

                {"PERMISSION:VIEW", "Xem danh sách quyền", "PERMISSION"},
                {"PERMISSION:CREATE", "Tạo quyền", "PERMISSION"},

                {"CATEGORY:VIEW", "Xem danh sách danh mục", "CATEGORY"},
                {"CATEGORY:CREATE", "Tạo danh mục", "CATEGORY"},
                {"CATEGORY:UPDATE", "Cập nhật danh mục", "CATEGORY"},
                {"CATEGORY:DELETE", "Xóa danh mục", "CATEGORY"},
                {"CATEGORY:RESTORE", "Khôi phục danh mục", "CATEGORY"},

                {"VOUCHER:VIEW", "Xem danh sách voucher", "VOUCHER"},
                {"VOUCHER:CREATE", "Tạo voucher", "VOUCHER"},
                {"VOUCHER:UPDATE", "Cập nhật voucher", "VOUCHER"},
                {"VOUCHER:DELETE", "Xóa voucher", "VOUCHER"},
                {"VOUCHER:RESTORE", "Khôi phục voucher", "VOUCHER"},

                {"EVENT:VIEW", "Xem danh sách sự kiện", "EVENT"},
                {"EVENT:CREATE", "Tạo sự kiện", "EVENT"},
                {"EVENT:UPDATE", "Cập nhật sự kiện", "EVENT"},
                {"EVENT:DELETE", "Xóa sự kiện", "EVENT"},
                {"EVENT:APPROVE", "Duyệt sự kiện", "EVENT"},
                {"EVENT:REJECT", "Từ chối sự kiện", "EVENT"},

                {"RESERVATION:VIEW", "Xem danh sách đặt chỗ", "RESERVATION"}
        };

        if (adminRole.getPermissions() == null) {
            adminRole.setPermissions(new java.util.HashSet<>());
        }

        for (String[] p : permissions) {
            Permission permission = permissionRepository.findByCode(p[0]);
            if (permission == null) {
                permission = new Permission();
                permission.setCode(p[0]);
                permission.setName(p[1]);
                permission.setModule(p[2]);
                permission.setDisable(false);
                permission.setCreatedAt(LocalDateTime.now());
                permission.setUpdatedAt(LocalDateTime.now());
                permissionRepository.save(permission);
            }
            if (!adminRole.getPermissions().contains(permission)) {
                adminRole.getPermissions().add(permission);
            }
        }
        roleRepository.save(adminRole);

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
