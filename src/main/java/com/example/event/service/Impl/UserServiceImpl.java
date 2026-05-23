package com.example.event.service.Impl;

import com.example.event.config.security.SecurityUtils;
import com.example.event.config.security.jwt.JwtUtils;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.FileStatus;
import com.example.event.constant.FileType;
import com.example.event.constant.UserStatus;
import com.example.event.dto.UserDTO;
import com.example.event.dto.request.ChangePasswordReq;
import com.example.event.dto.request.CreateUserReq;
import com.example.event.dto.request.UpdateUserReq;
import com.example.event.dto.response.AuthResponse;
import com.example.event.entity.File;
import com.example.event.entity.Role;
import com.example.event.entity.Session;
import com.example.event.entity.User;
import com.example.event.exception.AppException;
import com.example.event.mapper.AuthMapper;
import com.example.event.mapper.UserMapper;
import com.example.event.repository.FileRepository;
import com.example.event.repository.ReservationRepository;
import com.example.event.repository.RoleRepository;
import com.example.event.repository.SessionRepository;
import com.example.event.repository.UserRepository;
import com.example.event.service.UserService;
import com.example.event.specification.UserSpecification;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final SecurityUtils securityUtils;
    private final PasswordEncoder passwordEncoder;
    private final FileRepository fileRepository;
    private final ReservationRepository reservationRepository;
    private final JwtUtils jwtUtils;
    private final SessionRepository sessionRepository;
    private final AuthMapper authMapper;

    private static final Map<UserStatus, List<UserStatus>> STATUS_TRANSITIONS = new HashMap<>();

    static {
        STATUS_TRANSITIONS.put(UserStatus.ACTIVE, Arrays.asList(UserStatus.INACTIVE, UserStatus.BLOCKED, UserStatus.DELETED));
        STATUS_TRANSITIONS.put(UserStatus.INACTIVE, Arrays.asList(UserStatus.ACTIVE, UserStatus.BLOCKED, UserStatus.DELETED));
        STATUS_TRANSITIONS.put(UserStatus.BLOCKED, Arrays.asList(UserStatus.ACTIVE, UserStatus.INACTIVE, UserStatus.DELETED));
        STATUS_TRANSITIONS.put(UserStatus.UNVERIFIED, Arrays.asList(UserStatus.ACTIVE, UserStatus.BLOCKED, UserStatus.DELETED));
        STATUS_TRANSITIONS.put(UserStatus.DELETED, Arrays.asList(UserStatus.ACTIVE));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDTO> getUserSearch(String keyword, String roleId, String status, int pageNo, int pageSize) {
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Specification<User> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("avatar", JoinType.LEFT);
                root.fetch("role", JoinType.LEFT);
            }
            return cb.conjunction();
        };
        if (keyword != null) {
            spec = spec.and(Specification.anyOf(UserSpecification.hasEmail(keyword), UserSpecification.hasId(keyword), UserSpecification.hasName(keyword)));
        }
        if (roleId != null && !roleId.trim().isEmpty()) {
            spec = spec.and(UserSpecification.hasRoleId(roleId));
        }
        if (status != null && !status.trim().isEmpty()) {
            switch (status.toLowerCase()) {
                case "all":
                    spec = spec.and(UserSpecification.isNotDeleted());
                    break;
                case "deleted":
                    spec = spec.and(UserSpecification.isDeleted());
                    break;
                case "active":
                    spec = spec.and(UserSpecification.isNotDeleted())
                            .and(UserSpecification.hasStatus(UserStatus.ACTIVE));
                    break;
                case "inactive":
                    spec = spec.and(UserSpecification.isNotDeleted())
                            .and(UserSpecification.hasStatus(UserStatus.INACTIVE));
                    break;
                case "blocked":
                    spec = spec.and(UserSpecification.isNotDeleted())
                            .and(UserSpecification.hasStatus(UserStatus.BLOCKED));
                    break;
                case "unverified":
                    spec = spec.and(UserSpecification.isNotDeleted())
                            .and(UserSpecification.hasStatus(UserStatus.UNVERIFIED));
                    break;
            }
        }
        Page<User> users = userRepository.findAll(spec, pageable);
        return users.map(user -> userMapper.toDTO(user));
    }

    @Override
    @Transactional
    public UserDTO createUser(CreateUserReq req) {
        String creatorId = securityUtils.getCurrentUserId();
        if (userRepository.existsUserByEmail(req.getEmail())) {
            throw new AppException(ErrorCode.USER_EXISTS);
        }
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }
        Role role = Optional.ofNullable(roleRepository.findRoleById(req.getRoleId())).orElseThrow(
                () -> new AppException(ErrorCode.ROLE_NOT_FOUND)
        );
        User createUser = new User();
        createUser.setEmail(req.getEmail());
        createUser.setName(req.getName());
        createUser.setPassword(passwordEncoder.encode(req.getPassword()));
        createUser.setRole(role);
        createUser.setAddress(req.getAddress());
        createUser.setPhone(req.getPhone());
        createUser.setDob(req.getDob());
        createUser.setVerified(true);
        createUser.setStatus(UserStatus.ACTIVE);
        createUser.setVerifiedAt(LocalDateTime.now());
        createUser.setCreatedAt(LocalDateTime.now());
        createUser.setCreatedBy(creatorId);
        createUser.setUpdatedAt(LocalDateTime.now());
        createUser.setUpdatedBy(creatorId);
        userRepository.save(createUser);
        return userMapper.toDTO(createUser);
    }

    @Override
    @Transactional
    public UserDTO updateUser(UpdateUserReq req, String id) {
        String updatorId = securityUtils.getCurrentUserId();
        User updateUser = Optional.ofNullable(userRepository.findUserByIdForUpdate(id)).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_FOUND)
        );
        Role role = Optional.ofNullable(roleRepository.findRoleById(req.getRoleId())).orElseThrow(
                () -> new AppException(ErrorCode.ROLE_NOT_FOUND)
        );
        updateUser.setName(req.getName());
        updateUser.setPhone(req.getPhone());
        updateUser.setRole(role);
        if (req.getStatus() != null && req.getStatus() != updateUser.getStatus()) {
            List<UserStatus> allowedTransitions = STATUS_TRANSITIONS.get(updateUser.getStatus());
            if (allowedTransitions == null || !allowedTransitions.contains(req.getStatus())) {
                throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION);
            }
            updateUser.setStatus(req.getStatus());
        }
        if (req.getDob() != null) updateUser.setDob(req.getDob());
        if (req.getAddress() != null && !req.getAddress().trim().equals("")) updateUser.setAddress(req.getAddress());
        String newAvatarId = req.getFileId();
        File oldAvatar = updateUser.getAvatar();
        if (newAvatarId != null && !newAvatarId.trim().isEmpty()) {
            if (oldAvatar == null || !newAvatarId.equals(oldAvatar.getId())) {
                File newAvatar = Optional.ofNullable(fileRepository.findFileById(newAvatarId))
                        .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));
                if (newAvatar.getType() != FileType.IMAGE) {
                    throw new AppException(ErrorCode.INVALID_FILE_TYPE);
                }
                newAvatar.setStatus(FileStatus.ACTIVE);
                newAvatar.setReferenceId(updateUser.getId());
                fileRepository.save(newAvatar);
                if (oldAvatar != null) {
                    oldAvatar.setReferenceId(null);
                    oldAvatar.setStatus(FileStatus.DELETED);
                    oldAvatar.setDeletedAt(LocalDateTime.now());
                    fileRepository.save(oldAvatar);
                }
                updateUser.setAvatar(newAvatar);
            }
        }
        updateUser.setUpdatedAt(LocalDateTime.now());
        updateUser.setUpdatedBy(updatorId);
        userRepository.save(updateUser);
        return userMapper.toDTO(updateUser);
    }

    @Override
    @Transactional
    public UserDTO updateProfile(UpdateUserReq req) {
        String updatorId = securityUtils.getCurrentUserId();
        User updateUser = Optional.ofNullable(userRepository.findUserByIdForUpdate(updatorId)).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_FOUND)
        );
        updateUser.setName(req.getName());
        updateUser.setPhone(req.getPhone());
        if (req.getDob() != null) updateUser.setDob(req.getDob());
        if (req.getAddress() != null && !req.getAddress().trim().equals("")) updateUser.setAddress(req.getAddress());
        String newAvatarId = req.getFileId();
        File oldAvatar = updateUser.getAvatar();
        if (newAvatarId != null && !newAvatarId.trim().isEmpty()) {
            if (oldAvatar == null || !newAvatarId.equals(oldAvatar.getId())) {
                File newAvatar = Optional.ofNullable(fileRepository.findFileById(newAvatarId))
                        .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));
                if (newAvatar.getType() != FileType.IMAGE) {
                    throw new AppException(ErrorCode.INVALID_FILE_TYPE);
                }
                newAvatar.setStatus(FileStatus.ACTIVE);
                newAvatar.setReferenceId(updateUser.getId());
                fileRepository.save(newAvatar);
                if (oldAvatar != null) {
                    oldAvatar.setReferenceId(null);
                    oldAvatar.setStatus(FileStatus.DELETED);
                    oldAvatar.setDeletedAt(LocalDateTime.now());
                    fileRepository.save(oldAvatar);
                }
                updateUser.setAvatar(newAvatar);
            }
        }
        updateUser.setUpdatedAt(LocalDateTime.now());
        updateUser.setUpdatedBy(updatorId);
        userRepository.save(updateUser);
        return userMapper.toDTO(updateUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO findUserById(String id) {
        User user = Optional.ofNullable(userRepository.findUserByIdWithDetails(id)).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_FOUND)
        );
        return userMapper.toDTO(user);
    }

    @Override
    @Transactional
    public UserDTO restoreUser(String id) {
        String restorId = securityUtils.getCurrentUserId();
        User user = Optional.ofNullable(userRepository.findUserById(id)).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_FOUND)
        );
        if (user.getDeletedAt() == null) {
            throw new AppException(ErrorCode.USER_NOT_IN_TRASH);
        }
        String email = user.getEmail().replaceAll("-deleted-\\d+$", "");
        if (userRepository.existsUserByEmailAndIdNot(email, id)) {
            throw new AppException(ErrorCode.USER_EXISTS);
        }
        user.setEmail(email);
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(restorId);
        user.setDeletedAt(null);
        user.setDeletedBy(null);
        userRepository.save(user);
        return userMapper.toDTO(user);
    }

    @Override
    @Transactional
    public UserDTO softDeleteUser(String id) {
        String deletorId = securityUtils.getCurrentUserId();
        User user = Optional.ofNullable(userRepository.findUserById(id)).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_FOUND)
        );
        if (user.getDeletedAt() != null) {
            throw new AppException(ErrorCode.USER_ALREADY_DELETED);
        }
        if (reservationRepository.existsByUserIdAndDeletedAtIsNull(id)) {
            throw new AppException(ErrorCode.USER_HAS_RESERVATIONS);
        }
        user.setEmail(user.getEmail() + "-deleted-" + System.currentTimeMillis());
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(deletorId);
        user.setDeletedAt(LocalDateTime.now());
        user.setDeletedBy(deletorId);
        userRepository.save(user);
        return userMapper.toDTO(user);
    }
    
    @Override
    @Transactional
    public AuthResponse changePassword(ChangePasswordReq req, String deviceId) {
        String currentUserId = securityUtils.getCurrentUserId();
        User user = Optional.ofNullable(userRepository.findUserByIdForUpdate(currentUserId)).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_FOUND)
        );

        Map<String, String> errors = new HashMap<>();

        // 1. Verify current password
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            errors.put("currentPassword", ErrorCode.CURRENT_PASSWORD_INCORRECT.getMessage());
        }

        // 2. Check if newPassword matches confirmPassword
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            errors.put("confirmPassword", ErrorCode.PASSWORD_MISMATCH.getMessage());
        }

        // Throw all validation errors at once if any
        if (!errors.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, errors);
        }

        // 3. Hash newPassword and save
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));

        // 4. Increment tokenVersion to invalidate other sessions
        user.setTokenVersion(user.getTokenVersion() + 1);

        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(currentUserId);
        userRepository.save(user);

        // 5. Generate new session for "silent refresh"
        String refreshToken = jwtUtils.generateToken(user.getEmail(), null, "refresh");
        Session session = new Session();
        session.setUser(user);
        session.setRevoked(false);
        session.setRefreshToken(refreshToken);
        session.setDeviceId(deviceId);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiryDate(jwtUtils.getExpiryDate(refreshToken).toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime());
        sessionRepository.save(session);

        String accessToken = jwtUtils.generateToken(user.getEmail(), session.getId(), "access");

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(authMapper.toDTO(user))
                .build();
    }
}
