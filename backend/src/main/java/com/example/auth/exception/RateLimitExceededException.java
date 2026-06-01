package com.example.auth.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends AuthException {

    public RateLimitExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
