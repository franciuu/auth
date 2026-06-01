package com.example.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for authentication/authorization flows. Carries an HTTP
 * status and a client-safe (generic) message.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
