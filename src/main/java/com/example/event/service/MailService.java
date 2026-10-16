package com.example.event.service;

public interface MailService {
    void registerUser(String verifyToken, String email);
    void sendForgotPasswordOtp(String email, String otp);
}
