package com.example.event.service.Impl;

import com.example.event.component.AuthForgotPasswordProducer;
import com.example.event.component.AuthVerifyEmailProducer;
import com.example.event.config.security.SecurityUtils;
import com.example.event.config.security.jwt.JwtUtils;
import com.example.event.config.security.jwt.RefreshTokenUtils;
import com.example.event.config.security.user.CustomUserDetails;
import com.example.event.constant.ErrorCode;
import com.example.event.constant.FileFolder;
import com.example.event.constant.FileStatus;
import com.example.event.constant.FileType;
import com.example.event.constant.SessionRevokeReason;
import com.example.event.constant.UserStatus;
import com.example.event.dto.AuthDTO;
import com.example.event.dto.ForgotPasswordTokenData;
import com.example.event.dto.request.ForgotPasswordResetReq;
import com.example.event.dto.request.ForgotPasswordSendOtpReq;
import com.example.event.dto.request.ForgotPasswordVerifyOtpReq;
import com.example.event.dto.request.LoginReq;
import com.example.event.dto.request.SignUpReq;
import com.example.event.dto.response.AuthResponse;
import com.example.event.dto.response.AuthTokensResponse;
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
import lombok.extern.slf4j.Slf4j;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final SessionRepository sessionRepository;
    private final RoleRepository roleRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenUtils refreshTokenUtils;
    private final SecurityUtils securityUtils;
    private final AuthMapper authMapper;
    private final RedisService redisService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final Long maxRetries = 5L;
    private final String loginFailPrefix = "login:fail:";
    private final String tokenPrefix = "auth:";

    // Constants cho tính năng Quên mật khẩu / Reset Password
    private static final String FORGOT_TOKEN_PREFIX = "forgot:token:";
    private static final String FORGOT_OTP_PREFIX = "forgot:otp:";
    private static final Long FORGOT_PASSWORD_TOKEN_TTL_SECONDS = 900L;      // 15 phút: thời gian sống của phiên resetToken
    private static final Long FORGOT_PASSWORD_OTP_TTL_SECONDS = 300L;        // 5 phút: thời gian sống của mã xác thực OTP
    private static final Long FORGOT_PASSWORD_VERIFIED_TTL_SECONDS = 600L;   // 10 phút: thời gian cho phép đổi mật khẩu sau khi verify OTP thành công
    private static final String FORGOT_COOLDOWN_PREFIX = "forgot:cooldown:email:";
    private static final Long FORGOT_PASSWORD_COOLDOWN_SECONDS = 60L;       // 60 giây giãn cách giữa các lần yêu cầu gửi OTP

    // Constants cho tính năng Xác thực tài khoản đăng ký
    private static final String RESEND_VERIFY_COOLDOWN_PREFIX = "auth:cooldown:verify:";
    private static final Long RESEND_VERIFY_COOLDOWN_SECONDS = 60L;         // 60 giây giãn cách gửi lại email xác thực

    private final UserRepository userRepository;
    private final MailService mailService;
    private final FileRepository fileRepository;
    private final AuthVerifyEmailProducer authVerifyEmailProducer;
    private final AuthForgotPasswordProducer authForgotPasswordProducer;
    @Value("${jwt.verify-expiration}")
    private Long verifyExpiration;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

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
    @Transactional(noRollbackFor = AppException.class)
    public AuthTokensResponse refreshToken(String refreshToken, String deviceId) {
        log.info("[AUTH] Bắt đầu xử lý yêu cầu làm mới refresh token.");
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("[AUTH] Từ chối refresh vì request không có refresh token.");
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        String refreshTokenHash = refreshTokenUtils.hashToken(refreshToken);
        Session session = sessionRepository.findByRefreshTokenHashForUpdate(refreshTokenHash)
                .orElseThrow(() -> {
                    log.warn("[AUTH] Từ chối refresh vì không tìm thấy session cho token đã gửi.");
                    return new AppException(ErrorCode.TOKEN_INVALID);
                });
        String tokenFamily = session.getTokenFamily();
        String sid = session.getId();

        if (!Objects.equals(deviceId, session.getDeviceId())) {
            log.warn("[AUTH] Từ chối refresh do device id không khớp, session id={}", sid);
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        if (session.isRevoked()) {
            SessionRevokeReason reuseReason = session.getRevokeReason() == SessionRevokeReason.TOKEN_ROTATED
                    ? SessionRevokeReason.REFRESH_TOKEN_REUSE
                    : SessionRevokeReason.REVOKED_TOKEN_REUSE;
            log.warn("[AUTH] Phát hiện gửi lại refresh token đã thu hồi, session id={}, nguyên nhân={}", sid, reuseReason);
            revokeTokenFamily(tokenFamily, reuseReason);
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        LocalDateTime now = LocalDateTime.now();
        if (session.getTokenFamilyExpiresAt() == null
                || !session.getTokenFamilyExpiresAt().isAfter(now)) {
            // Hạn family cố định theo refresh token đầu tiên, không được kéo dài khi rotation.
            log.warn("[AUTH] Từ chối refresh vì token family đã hết hạn, session id={}", sid);
            revokeTokenFamily(tokenFamily, SessionRevokeReason.TOKEN_FAMILY_EXPIRED);
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        if (session.getExpiryDate() == null || !session.getExpiryDate().isAfter(now)) {
            log.warn("[AUTH] Từ chối refresh vì refresh token đã hết hạn, session id={}", sid);
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = session.getUser();
        if (session.getTokenVersion() != null
                && !session.getTokenVersion().equals(user.getTokenVersion())) {
            log.warn("[AUTH] Từ chối refresh vì token version đã thay đổi, session id={}", sid);
            revokeTokenFamily(tokenFamily, SessionRevokeReason.PASSWORD_CHANGED);
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        // Mỗi lần rotation tạo một row mới nhưng giữ nguyên family và deadline tuyệt đối.
        Session rotatedSession = new Session();
        rotatedSession.setUser(user);
        rotatedSession.setDeviceId(session.getDeviceId());
        rotatedSession.setTokenFamily(session.getTokenFamily());
        rotatedSession.setTokenFamilyExpiresAt(session.getTokenFamilyExpiresAt());
        rotatedSession.setTokenVersion(user.getTokenVersion());
        rotatedSession.setCreatedAt(now);
        rotatedSession.setRevoked(false);
        sessionRepository.saveAndFlush(rotatedSession);

        // Client nhận UUID mới; database chỉ lưu hash và hạn không vượt deadline family.
        String rotatedRefreshToken = refreshTokenUtils.generateToken();
        rotatedSession.setRefreshTokenHash(refreshTokenUtils.hashToken(rotatedRefreshToken));
        rotatedSession.setExpiryDate(refreshTokenUtils.getRotatedExpiry(
            now, rotatedSession.getTokenFamilyExpiresAt()));
        sessionRepository.save(rotatedSession);

        // Đánh dấu token cũ đã rotate; lần dùng lại nó sẽ kích hoạt thu hồi family.
        session.setRevoked(true);
        session.setRevokeReason(SessionRevokeReason.TOKEN_ROTATED);
        session.setRevokedAt(now);
        sessionRepository.save(session);

        String accessToken = jwtUtils.generateToken(user.getEmail(), rotatedSession.getId(), "access");
        log.info("[AUTH] Làm mới token thành công; session cũ id={}, session mới id={}",
            session.getId(), rotatedSession.getId());
        return new AuthTokensResponse(accessToken, rotatedRefreshToken);
    }

    private void revokeTokenFamily(String tokenFamily, SessionRevokeReason reason) {
        List<Session> familySessions = sessionRepository.findAllByTokenFamilyForUpdate(tokenFamily);
        if (!familySessions.isEmpty()) {
            revokeFamily(familySessions, reason);
        }
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        Claims claims = jwtUtils.extractAllClaims(accessToken);
        String sid = claims.get("sid", String.class);
        if (!"access".equalsIgnoreCase(claims.get("type", String.class)) || sid == null) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
        Session session = sessionRepository.findSessionByIdForUpdate(sid)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));
        if (session.getTokenFamily() == null) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        // Logout cần lấy toàn family để thu hồi tất cả session liên quan.
        List<Session> familySessions = sessionRepository.findAllByTokenFamilyForUpdate(session.getTokenFamily());
        if (!familySessions.isEmpty()) {
            revokeFamily(familySessions, SessionRevokeReason.LOGOUT);
        }
    }

    private void revokeFamily(List<Session> sessions, SessionRevokeReason reason) {
        // Ghi lý do thu hồi và blacklist sid trong thời gian access token còn hiệu lực.
        LocalDateTime now = LocalDateTime.now();
        long blacklistSeconds = Math.max(1, (jwtUtils.getAccessExpirationMillis() + 999) / 1000);
        for (Session familySession : sessions) {
            familySession.setRevoked(true);
            familySession.setRevokeReason(reason);
            familySession.setRevokedAt(now);
            if (familySession.getId() != null) {
                sessionService.addToBlackList(familySession.getId(), blacklistSeconds);
            }
        }
        sessionRepository.saveAll(sessions);
    }

    private LocalDateTime toLocalDateTime(java.util.Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
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

        // Xóa cooldown gửi lại khi tài khoản đã xác thực thành công
        redisService.del(RESEND_VERIFY_COOLDOWN_PREFIX + user.getEmail().toLowerCase());
    }

    @Transactional
    @Override
    public void resendVerify(String email) {
        String cleanEmail = email.trim().toLowerCase();

        // 1. Kiểm tra Cooldown theo Email (chặn spam vào cùng 1 hòm thư)
        String cooldownKey = RESEND_VERIFY_COOLDOWN_PREFIX + cleanEmail;
        if (redisService.get(cooldownKey, Object.class) != null) {
            throw new AppException(ErrorCode.RESEND_VERIFY_COOLDOWN);
        }

        User existedUser = userRepository.findUserByEmail(cleanEmail);
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

        // 2. Thiết lập cooldown 60 giây trong Redis
        redisService.set(cooldownKey, "1", RESEND_VERIFY_COOLDOWN_SECONDS);
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

        // Thiết lập cooldown 60 giây ngay sau khi đăng ký và gửi email kích hoạt đầu tiên
        redisService.set(RESEND_VERIFY_COOLDOWN_PREFIX + createUser.getEmail().toLowerCase(), "1", RESEND_VERIFY_COOLDOWN_SECONDS);
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
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        // Lưu session trước để JWT đầu tiên mang sid; exp của JWT này trở thành
        // deadline bất biến của family.
        Session session = new Session();
        session.setUser(user);
        session.setRevoked(false);
        session.setDeviceId(deviceId);
        session.setCreatedAt(createdAt);
        session.setTokenFamily(UUID.randomUUID().toString());
        sessionRepository.saveAndFlush(session);

        String refreshToken = refreshTokenUtils.generateToken();
        LocalDateTime initialExpiry = refreshTokenUtils.getInitialExpiry(createdAt);
        session.setRefreshTokenHash(refreshTokenUtils.hashToken(refreshToken));
        session.setExpiryDate(initialExpiry);
        session.setTokenFamilyExpiresAt(initialExpiry);
        session.setTokenVersion(user.getTokenVersion());
        sessionRepository.save(session);

        String accessToken = jwtUtils.generateToken(user.getEmail(), session.getId(), "access");
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
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

        String targetOrigin = (frontendUrl != null && !frontendUrl.isBlank())
                ? frontendUrl.replaceAll("/+$", "")
                : "http://localhost:5173";

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
                            '%s'
                        );
                    }
                    setTimeout(() => {
                        window.close();
                    }, 500);
                </script>
                </body>
                </html>
                """;

        return String.format(htmlTemplate, type, payload, targetOrigin);
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

    @Override
    @Transactional
    public String sendForgotPasswordOtp(ForgotPasswordSendOtpReq req) {
        String email = req.getEmail().trim().toLowerCase();

        User user = Optional.ofNullable(userRepository.findUserByEmail(email))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getDeletedAt() != null || user.getStatus() == UserStatus.DELETED) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AppException(ErrorCode.USER_BLOCKED);
        }
        if (!user.isVerified() || user.getStatus() == UserStatus.UNVERIFIED) {
            throw new AppException(ErrorCode.USER_NOT_VERIFIED);
        }
        if ("GOOGLE".equalsIgnoreCase(user.getProvider()) || user.getPassword() == null) {
            throw new AppException(ErrorCode.ACCOUNT_REGISTERED_WITH_GOOGLE);
        }

        // Kiểm tra Rate Limit: giãn cách tối thiểu 60s giữa các lần yêu cầu gửi OTP cho cùng 1 email
        String cooldownKey = FORGOT_COOLDOWN_PREFIX + email;
        if (redisService.get(cooldownKey, Object.class) != null) {
            throw new AppException(ErrorCode.OTP_RESEND_COOLDOWN);
        }

        // Kiểm tra xem token gửi lên có tồn tại trong Redis và đúng với email này không
        String resetToken = req.getResetToken();
        boolean reuseToken = false;
        if (resetToken != null && !resetToken.isBlank()) {
            Object rawData = redisService.get(FORGOT_TOKEN_PREFIX + resetToken, Object.class);
            if (rawData != null) {
                ForgotPasswordTokenData tokenData = new ObjectMapper().convertValue(rawData, ForgotPasswordTokenData.class);
                if (tokenData != null && email.equalsIgnoreCase(tokenData.getEmail())) {
                    reuseToken = true;
                }
            }
        }

        if (!reuseToken) {
            resetToken = UUID.randomUUID().toString();
        }

        // Sinh mã OTP ngẫu nhiên 6 chữ số
        String otp = String.format("%06d", new java.util.Random().nextInt(1_000_000));

        // Lưu / Ghi đè vào Redis:
        // Key 1: forgot:token:{resetToken} -> Quản lý phiên làm việc, TTL = 15 phút
        ForgotPasswordTokenData tokenData = ForgotPasswordTokenData.builder()
                .email(email)
                .verified(false)
                .build();
        redisService.set(FORGOT_TOKEN_PREFIX + resetToken, tokenData, FORGOT_PASSWORD_TOKEN_TTL_SECONDS);

        // Key 2: forgot:otp:{resetToken} -> Quản lý mã OTP, TTL = đúng 5 phút
        // Việc ghi đè key này đồng nghĩa OTP cũ (nếu có) bị hủy bỏ ngay lập tức
        redisService.set(FORGOT_OTP_PREFIX + resetToken, otp, FORGOT_PASSWORD_OTP_TTL_SECONDS);

        // Gửi email chứa mã OTP qua Outbox Pattern + Redis Stream (Asynchronous)
        authForgotPasswordProducer.sendForgotPasswordOtpEmail(email, otp);

        // Lưu cooldown 60 giây ngăn spam
        redisService.set(cooldownKey, "1", FORGOT_PASSWORD_COOLDOWN_SECONDS);

        log.info("[FORGOT-PASSWORD] Đã queue gửi OTP đến email {} với token {}", email, resetToken);
        return resetToken;
    }

    @Override
    public void verifyForgotPasswordOtp(ForgotPasswordVerifyOtpReq req) {
        String email = req.getEmail().trim().toLowerCase();
        String resetToken = req.getResetToken();
        String inputOtp = req.getOtp().trim();

        // 1. Kiểm tra Key 1 (Phiên token)
        Object rawData = redisService.get(FORGOT_TOKEN_PREFIX + resetToken, Object.class);
        if (rawData == null) {
            throw new AppException(ErrorCode.RESET_TOKEN_INVALID);
        }

        ForgotPasswordTokenData tokenData = new ObjectMapper().convertValue(rawData, ForgotPasswordTokenData.class);
        if (tokenData == null || !email.equalsIgnoreCase(tokenData.getEmail())) {
            throw new AppException(ErrorCode.RESET_TOKEN_INVALID);
        }

        // 2. Kiểm tra Key 2 (Mã OTP)
        String storedOtp = redisService.get(FORGOT_OTP_PREFIX + resetToken, String.class);
        if (storedOtp == null) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (!storedOtp.equals(inputOtp)) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        // 3. Khớp OTP: Xóa Key OTP ngay để không thể tái sử dụng
        redisService.del(FORGOT_OTP_PREFIX + resetToken);

        // 4. Cập nhật trạng thái verified = true và gia hạn thêm 10 phút để nhập mật khẩu
        tokenData.setVerified(true);
        redisService.set(FORGOT_TOKEN_PREFIX + resetToken, tokenData, FORGOT_PASSWORD_VERIFIED_TTL_SECONDS);

        log.info("[FORGOT-PASSWORD] Xác thực OTP thành công cho email {} với token {}", email, resetToken);
    }

    @Override
    @Transactional
    public void resetPassword(ForgotPasswordResetReq req) {
        String resetToken = req.getResetToken();

        // 1. Kiểm tra mật khẩu khớp nhau
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 2. Kiểm tra Key 1 và trạng thái verified
        Object rawData = redisService.get(FORGOT_TOKEN_PREFIX + resetToken, Object.class);
        if (rawData == null) {
            throw new AppException(ErrorCode.RESET_TOKEN_INVALID);
        }

        ForgotPasswordTokenData tokenData = new ObjectMapper().convertValue(rawData, ForgotPasswordTokenData.class);
        if (tokenData == null || !tokenData.isVerified()) {
            throw new AppException(ErrorCode.OTP_NOT_VERIFIED);
        }

        // 3. Tìm user và cập nhật mật khẩu mới
        User user = Optional.ofNullable(userRepository.findUserByEmail(tokenData.getEmail()))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getDeletedAt() != null || user.getStatus() == UserStatus.DELETED) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AppException(ErrorCode.USER_BLOCKED);
        }
        if (!user.isVerified() || user.getStatus() == UserStatus.UNVERIFIED) {
            throw new AppException(ErrorCode.USER_NOT_VERIFIED);
        }
        if ("GOOGLE".equalsIgnoreCase(user.getProvider()) || user.getPassword() == null) {
            throw new AppException(ErrorCode.ACCOUNT_REGISTERED_WITH_GOOGLE);
        }

        // Kiểm tra mật khẩu mới không được trùng với mật khẩu hiện tại
        if (user.getPassword() != null && passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1); // Vô hiệu hóa các phiên đăng nhập cũ
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy("SYSTEM_FORGOT_PASSWORD");
        userRepository.save(user);

        // 4. Xóa token phiên khỏi Redis
        redisService.del(FORGOT_TOKEN_PREFIX + resetToken);

        log.info("[FORGOT-PASSWORD] Đặt lại mật khẩu thành công cho user {}", user.getEmail());
    }
}
