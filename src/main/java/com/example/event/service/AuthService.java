package com.example.event.service;

import com.example.event.dto.AuthDTO;
import com.example.event.dto.request.LoginReq;
import com.example.event.dto.request.SignUpReq;
import com.example.event.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse login(LoginReq req, String deviceId, String ipAddress);
    AuthDTO getAuthInfo();
    String refreshToken(String refreshToken, String deviceId);
    void logout(String accessToken);
    void verify(String verifyToken);
    void resendVerify(String email);
    void signup(SignUpReq req, String deviceId);
}
