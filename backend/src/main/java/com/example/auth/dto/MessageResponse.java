package com.example.auth.dto;

/**
 * Generic success/message envelope. Messages are intentionally generic to
 * avoid user enumeration.
 */
public record MessageResponse(
        boolean success,
        String message
) {
    public static MessageResponse ok(String message) {
        return new MessageResponse(true, message);
    }

    public static MessageResponse fail(String message) {
        return new MessageResponse(false, message);
    }
}
