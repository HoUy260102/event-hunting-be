package com.example.event.constant;

public enum SessionRevokeReason {
    LOGOUT,
    TOKEN_ROTATED,
    REFRESH_TOKEN_REUSE,
    REVOKED_TOKEN_REUSE,
    TOKEN_FAMILY_EXPIRED,
    PASSWORD_CHANGED,
    ADMIN_REVOKED,
    OTHER
}