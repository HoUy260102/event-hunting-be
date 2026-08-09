package com.example.event.service.Impl;

import com.example.event.component.AuthVerifyEmailProducer;
import com.example.event.config.security.SecurityUtils;
import com.example.event.config.security.jwt.JwtUtils;
import com.example.event.config.security.user.CustomUserDetails;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.FileFolder;
import com.example.event.constant.FileStatus;
import com.example.event.constant.FileType;
import com.example.event.constant.UserStatus;
import com.example.event.dto.AuthDTO;
import com.example.event.dto.request.LoginReq;
import com.example.event.dto.request.SignUpReq;
import com.example.event.dto.response.AuthResponse;
import com.example.event.entity.File;
import com.example.event.entity.Role;
import com.example.event.entity.Session;
import com.example.event.entity.User;
import com.example.event.exception.AppException;
import com.example.event.exception.JwtAuthenticationException;
import com.example.event.mapper.AuthMapper;
import com.example.event.repository.FileRepository;
import com.example.event.repository.RoleRepository;
import com.example.event.repository.SessionRepository;
import com.example.event.repository.UserRepository;
import com.example.event.service.AuthService;
import com.example.event.service.MailService;
import com.example.event.service.RedisService;
import com.example.event.service.SessionService;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final SessionRepository sessionRepository;
    private final RoleRepository roleRepository;
    private final JwtUtils jwtUtils;
    private final SecurityUtils securityUtils;
    private final AuthMapper authMapper;
    private final RedisService redisService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final Long maxRetries = 5L;
    private final String loginFailPrefix = "login:fail:";
    private final String tokenPrefix = "auth:";
    private final UserRepository userRepository;
    private final MailService mailService;
    private final FileRepository fileRepository;
    private final AuthVerifyEmailProducer authVerifyEmailProducer;
    @Value("${jwt.verify-expiration}")
    private Long verifyExpiration;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    private final ObjectMapper objectMapper;

    @Transactional
    @Override
    public AuthResponse login(LoginReq req, String deviceId, String ipAddress) {
        try {
            if (!isLoginRetryBlocked(req.getUsername(), deviceId, ipAddress)) {
                throw new AppException(ErrorCode.LOGIN_BLOCKED);
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getUsername(),
                            req.getPassword()));

            String key = loginFailPrefix + req.getUsername() + ":" + deviceId + ":" + ipAddress;
            redisService.del(key);

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            // Kiểm tra user đã xác thực chưa
            if (!user.isVerified()) {
                throw new AppException(ErrorCode.USER_NOT_VERIFIED);
            }

            // Kiểm tra trạng thái tài khoản
            checkUserStatus(user);

            // Tạo auth response
            AuthResponse authResponse = buildAuthResponse(user, deviceId);
            return authResponse;
        } catch (BadCredentialsException e) {
            recordLoginFail(req.getUsername(), deviceId, ipAddress);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuthDTO getAuthInfo() {
        String userId = securityUtils.getCurrentUserId();
        User user = userRepository.findUserByIdWithDetails(userId);
        return authMapper.toDTO(user);
    }

    @Override
    public String refreshToken(String refreshToken, String deviceId) {
        Session session = sessionRepository.findByRefreshTokenAndDeviceId(refreshToken, deviceId);
        if (session == null)
            return "";
        User user = session.getUser();
        return jwtUtils.generateToken(user.getEmail(), session.getId(), "access");
    }

    @Override
    public void logout(String accessToken) {
        Claims claims = jwtUtils.extractAllClaims(accessToken);
        String sid = (String) claims.get("sid");
        Session session = Optional.ofNullable(sessionRepository.findSessionById(sid))
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));
        session.setRevoked(true);
        sessionRepository.save(session);
        if (session.getExpiryDate().isAfter(LocalDateTime.now())) {
            long duration = Duration.between(LocalDateTime.now(), session.getExpiryDate()).getSeconds();
            sessionService.addToBlackList(sid, duration);
        }
    }

    @Override
    public void verify(String verifyToken) {
        Claims claims = jwtUtils.extractAllClaims(verifyToken);
        String id = (String) claims.get("id");
        User user = userRepository.findUserById(id);
        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.isVerified()) {
            throw new AppException(ErrorCode.USER_VERIFIED);
        }

        String verifyKey = tokenPrefix + user.getEmail() + ":verify-token";
        String storedToken = redisService.get(verifyKey, String.class);
        if (storedToken == null || !storedToken.equals(verifyToken)) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_EXPIRED);
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setVerified(true);
        user.setVerifiedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(id);
        userRepository.save(user);
    }

    @Override
    public void resendVerify(String email) {
        User existedUser = userRepository.findUserByEmail(email);
        if (existedUser == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (existedUser.isVerified()) {
            throw new AppException(ErrorCode.USER_VERIFIED);
        }
        String verifyToken = jwtUtils.generateToken(existedUser.getEmail(), null, "verify");
        String verifyKey = tokenPrefix + existedUser.getEmail() + ":verify-token";
        Long ttl = verifyExpiration / 1000 + 5;
        redisService.set(verifyKey, verifyToken, ttl);
        authVerifyEmailProducer.sendVerifyEmail(existedUser.getEmail(), verifyToken);
    }

    @Transactional
    @Override
    public void signup(SignUpReq req, String deviceId) {
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }
        User existedUser = userRepository.findUserByEmail(req.getEmail());
        if (existedUser != null && existedUser.isVerified() == false) {
            throw new AppException(ErrorCode.USER_NOT_VERIFIED);
        }
        if (existedUser != null) {
            if ("GOOGLE".equals(existedUser.getProvider())) {
                throw new AppException(ErrorCode.ACCOUNT_REGISTERED_WITH_GOOGLE);
            }
            throw new AppException(ErrorCode.USER_EXISTS);
        }
        User createUser = new User();
        Role role = roleRepository.findByName("USER");
        createUser.setEmail(req.getEmail());
        createUser.setName(req.getName());
        createUser.setPassword(passwordEncoder.encode(req.getPassword()));
        createUser.setPhone(req.getPhone());
        createUser.setAddress(req.getAddress());
        createUser.setDob(req.getDob());
        createUser.setRole(role);
        createUser.setStatus(UserStatus.UNVERIFIED);
        createUser.setCreatedAt(LocalDateTime.now());
        createUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(createUser);
        createUser.setCreatedBy(createUser.getId());
        createUser.setUpdatedBy(createUser.getId());
        userRepository.save(createUser);
        String verifyToken = jwtUtils.generateToken(createUser.getEmail(), null, "verify");
        // Lưu verify token vào redis
        String verifyKey = tokenPrefix + createUser.getEmail() + ":verify-token";
        Long ttl = verifyExpiration / 1000 + 5;
        redisService.set(verifyKey, verifyToken, ttl);
        authVerifyEmailProducer.sendVerifyEmail(createUser.getEmail(), verifyToken);
    }

    @Override
    public String buildGoogleAuthUrl(String deviceId) {
        return new GoogleAuthorizationCodeRequestUrl(
                clientId,
                redirectUri,
                Arrays.asList("openid", "email", "profile")).setState(deviceId).build();
    }

    @Override
    @Transactional
    public String handleCallback(String code, String deviceId) throws IOException {
        try {
            // 1. Exchange code → id_token (1 request duy nhất)
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    new GsonFactory(),
                    clientId,
                    clientSecret,
                    code,
                    redirectUri).execute();

            // 2. Parse id_token lấy user info
            GoogleIdToken.Payload payload = tokenResponse.parseIdToken().getPayload();
            if (!payload.getEmailVerified()) {
                throw new RuntimeException("Email chưa được Google xác thực");
            }

            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            // 3. Upsert user vào DB
            User user = Optional.ofNullable(userRepository.findUserByEmail(email))
                    .orElseGet(() -> {
                        User newUser = new User();
                        newUser.setEmail(email);
                        newUser.setName(name);
                        newUser.setProvider("GOOGLE");
                        newUser.setCreatedAt(LocalDateTime.now());
                        newUser.setUpdatedAt(LocalDateTime.now());
                        newUser.setVerified(true);
                        newUser.setStatus(UserStatus.ACTIVE);
                        newUser.setVerifiedAt(LocalDateTime.now());
                        userRepository.save(newUser);
                        newUser.setCreatedBy(newUser.getId());
                        newUser.setUpdatedBy(newUser.getId());

                        File avatar = new File();
                        avatar.setUrl(picture);
                        avatar.setStatus(FileStatus.ACTIVE);
                        avatar.setType(FileType.IMAGE);
                        avatar.setFolder(FileFolder.USER_AVATAR);
                        avatar.setFormat("jpg");
                        avatar.setCreatedAt(LocalDateTime.now());
                        fileRepository.save(avatar);

                        Role role = roleRepository.findByName("USER");
                        newUser.setRole(role);
                        newUser.setAvatar(avatar);
                        return userRepository.save(newUser);
                    });
            // 4. Kiểm tra xem tài khoản có do google cấp không
            if (!"GOOGLE".equals(user.getProvider())) {
                return buildPopupHtml(null, "LOGIN_FAILURE",
                        "Tài khoản này trước đó đã đăng nhập bằng email và mật khẩu, hãy đăng nhập bằng cách thông thường.");
            }
            // 5. Tạo auth response
            AuthResponse authResponse = buildAuthResponse(user, deviceId);
            // 6. Trả về hệ thống
            return buildPopupHtml(authResponse, "LOGIN_SUCCESS", null);
        } catch (TokenResponseException e) {
            throw new RuntimeException("Google từ chối: " + e.getDetails().getErrorDescription());
        } catch (IOException e) {
            throw new RuntimeException("Không thể kết nối Google OAuth", e);
        }
    }

    private AuthResponse buildAuthResponse(User user, String deviceId) {
        // Tạo refresh token
        String refeshToken = jwtUtils.generateToken(user.getEmail(), null, "refresh");
        // Tìm kiếm session trong db nếu không có trả session mới
        Session session = new Session();
        session.setUser(user);
        session.setRevoked(false);
        session.setRefreshToken(refeshToken);
        session.setDeviceId(deviceId);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiryDate(jwtUtils.getExpiryDate(refeshToken).toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime());
        sessionRepository.save(session);
        // Tạo access token
        String accessToken = jwtUtils.generateToken(user.getEmail(), session.getId(), "access");
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refeshToken)
                .user(authMapper.toDTO(user))
                .build();
        return authResponse;
    }

    private String buildPopupHtml(AuthResponse authResponse, String type, String error) {
        String payload;

        if ("LOGIN_SUCCESS".equals(type)) {
            try {
                payload = objectMapper.writeValueAsString(authResponse);
            } catch (Exception e) {
                payload = "{}";
            }
        } else {
            // Tạo chuỗi JSON cho payload thất bại
            payload = String.format("{ error: '%s' }", error);
        }

        // Template HTML dùng String.format
        String htmlTemplate = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8"> <title>Authenticating...</title></head>
                <body>
                <script>
                    if (window.opener) {
                        window.opener.postMessage(
                            { type: '%s', payload: %s },
                            'http://localhost:5173'
                        );
                    }
                    setTimeout(() => {
                        window.close();
                    }, 500);
                </script>
                </body>
                </html>
                """;

        return String.format(htmlTemplate, type, payload);
    }

    private void recordLoginFail(String username, String deviceId, String ipAddress) {
        String key = loginFailPrefix + username + ":" + deviceId + ":" + ipAddress;
        Long count = redisService.incr(key, 1);
        if (count == 1) {
            redisService.expire(key, 600);
        }
    }

    private boolean isLoginRetryBlocked(String username, String deviceId, String ipAddress) {
        String key = loginFailPrefix + username + ":" + deviceId + ":" + ipAddress;
        Long count = Optional.ofNullable(redisService.get(key, Long.class)).orElse(0L);
        return count < maxRetries;
    }

    private void checkUserStatus(User user) {
        if (user.getDeletedAt() != null || user.getStatus() == UserStatus.DELETED) {
            throw new AppException(ErrorCode.USER_ALREADY_DELETED);
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AppException(ErrorCode.USER_BLOCKED);
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
        if (user.getStatus() == UserStatus.UNVERIFIED) {
            throw new AppException(ErrorCode.USER_NOT_VERIFIED);
        }
    }
}
