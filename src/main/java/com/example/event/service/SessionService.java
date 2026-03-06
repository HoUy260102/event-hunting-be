package com.example.event.service;

public interface SessionService {
    void addToBlackList(String sid, long duration);
    boolean isBlackList(String sid);
}
