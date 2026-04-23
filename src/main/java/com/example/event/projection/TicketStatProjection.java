package com.example.event.projection;

import com.example.event.constant.EventStatus;
import com.example.event.constant.ShowStatus;
import com.example.event.constant.TicketTierStatus;
import com.example.event.constant.TicketTypeStatus;

import java.time.LocalDateTime;

public interface TicketStatProjection {

    // ── Event ──────────────────────────────────────────
    String getEventId();
    String getEventName();
    String getEventLocation();
    LocalDateTime getEventStartTime();
    LocalDateTime getEventEndTime();
    EventStatus getEventStatus();
    String getEventPosterUrl();

    // ── Show ───────────────────────────────────────────
    String getShowId();
    LocalDateTime getShowStartTime();
    LocalDateTime getShowEndTime();
    ShowStatus getShowStatus();
    Integer getShowStartDay();      // EXTRACT(DAY ...)
    Integer getShowStartMonth();    // EXTRACT(MONTH ...)

    // ── TicketType ─────────────────────────────────────
    String getTicketTypeId();
    String getTypeTypeName();
    TicketTypeStatus getTicketTypeStatus();

    Integer getTicketTypeTotalQuantity();
    Integer getTicketTypeSoldQuantity();
    Integer getTicketTypeReservedQuantity();
    Integer getTicketTypeAvailableQuantity();

    // ── TicketTier ─────────────────────────────────────
    String getTicketTierId();
    String getTicketTierName();
    Long getTicketTierUnitPrice();
    TicketTierStatus getTicketTierStatus();

    Integer getTicketTierLimitQuantity();
    Integer getTicketTierSoldQuantity();
    Integer getTicketTierReservedQuantity();

    Long getTicketTierTotalPrice();
    Long getTicketTierTotalDiscountAmount();
    Long getTicketTierFinalPrice();
}