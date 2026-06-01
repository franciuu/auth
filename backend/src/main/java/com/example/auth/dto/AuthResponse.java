package com.example.auth.dto;

/**
 * Response DTO returned by login and refresh.
 * {@code expiresIn} is the access token lifetime in seconds.
 */
public record AuthResponse(
        boolean success,
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
