package com.example.auth.entity;

/**
 * Auditable security events persisted to the audit_logs table.
 */
public enum AuditEventType {
    USER_REGISTERED,
    LOGIN_SUCCESS,
    FAILED_LOGIN,
    TOKEN_REFRESHED,
    LOGOUT,
    UNAUTHORIZED_ACCESS,
    AUTHORIZATION_FAILURE,
    ADMIN_DISABLED_USER,
    EMAIL_VERIFIED
}
