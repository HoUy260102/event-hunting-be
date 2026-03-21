package com.example.event.service;

import com.example.event.dto.request.ReservationItemReq;
import com.example.event.entity.ReservationItem;

import java.util.List;

public interface LockService {
    void reserveUnassignedTickets(String showId, List<ReservationItemReq> requests);
    void lockSeats(String showId, List<ReservationItemReq> req, String userId);
    void unlockSeats(String showId, List<ReservationItemReq> req);
    void releaseUnassignedTickets(String showId, List<ReservationItemReq> requests);
    void releaseUnassignedReservationItem(String showId, List<ReservationItem> requests);
    void unlockSeatsReservationItem(String showId, List<ReservationItem> req);
}
