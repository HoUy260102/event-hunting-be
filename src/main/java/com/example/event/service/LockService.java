package com.example.event.service;

import com.example.event.dto.request.ReservationItemReq;
import com.example.event.entity.ReservationItem;

import java.util.List;

public interface LockService {
    void lockSeats(String showId, List<ReservationItemReq> req, String userId, String lockToken);
    void unlockSeats(String showId, List<ReservationItemReq> req, String lockToken);
    void unlockSeatsReservationItem(String showId, List<ReservationItem> req, String lockToken);
}
