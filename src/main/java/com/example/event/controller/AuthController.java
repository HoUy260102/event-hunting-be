package com.example.event.controller;

import com.example.event.dto.request.LoginReq;
import com.example.event.dto.request.SignUpReq;
import com.example.event.dto.response.ApiResponse;
import com.example.event.dto.response.AuthResponse;
import com.example.event.exception.AppException;
import com.example.event.exception.JwtAuthenticationException;
import com.example.event.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginReq loginReq, @RequestHeader(value = "X-Device-Id", defaultValue = "unknownDevice") String deviceId, HttpServletRequest request) {
        String ipAddress = request.getRemoteAddr();
        AuthResponse authResponse = authService.login(loginReq, deviceId, ipAddress);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .data(authResponse)
                .message("Thành công.")
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@Valid @RequestBody SignUpReq signUpReq, @RequestHeader(value = "X-Device-Id", defaultValue = "unknownDevice") String deviceId) {
        authService.signup(signUpReq, deviceId);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.CREATED.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam(name = "verifyToken") String verifyToken, @RequestParam(name = "email") String email) {
        try {
            authService.verify(verifyToken);
            messagingTemplate.convertAndSend("/topic/verify/" + email, "SUCCESS");
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(HttpStatus.OK.value())
                    .message("Xác thực tài khoản thành công.")
                    .build());
        } catch (JwtAuthenticationException e) {
            String errorMessage = e.getErrorCode().getMessage();
            messagingTemplate.convertAndSend("/topic/verify/" + email, errorMessage);
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .status(e.getErrorCode().getHttpStatus().value())
                    .message(errorMessage)
                    .build());
        } catch (AppException e) {
            String errorMessage = e.getErrorCode().getMessage();
            messagingTemplate.convertAndSend("/topic/verify/" + email, errorMessage);
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .status(e.getErrorCode().getHttpStatus().value())
                    .message(errorMessage)
                    .build());
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/verify/" + email, "Lỗi hệ thống");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Có lỗi xảy ra");
        }
    }

    @PostMapping("/resend-verify")
    public ResponseEntity<?> resendVerify(@RequestParam(name = "email") String email) {
        authService.resendVerify(email);
        return ResponseEntity.ok(ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> bodyReq, @RequestHeader(value = "X-Device-Id", defaultValue = "unknownDevice") String deviceId) {
        String refreshToken = bodyReq.get("refreshToken");
        String accessToken = authService.refreshToken(refreshToken, deviceId);
        Map<String, String> tokenRes = new HashMap<>();
        tokenRes.put("accessToken", accessToken);
        ApiResponse response = ApiResponse.builder()
                .data(tokenRes)
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String accessToken) {
        accessToken = accessToken.replace("Bearer ", "");
        authService.logout(accessToken);
        ApiResponse response = ApiResponse.builder()
                .status(HttpStatus.OK.value())
                .message("Thành công.")
                .build();
        return ResponseEntity.ok(response);
    }
}
