package com.example.event.service;

public interface MailService {
    void registerUser(String verifyToken, String email);
}
