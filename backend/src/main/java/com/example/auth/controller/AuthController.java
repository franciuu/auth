package com.example.auth.controller;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RefreshRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token refresh and logout")
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService authService;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.jwt.refresh-token-validity-seconds:604800}")
    private long refreshTokenValiditySeconds;

    @Operation(summary = "Register a new account")
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        // Generic response regardless of whether the email already existed.
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "If the details are valid, your account has been created."));
    }

    @Operation(summary = "Authenticate and receive access + refresh tokens")
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Map<String, Object> response = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        buildRefreshCookie((String) response.get("refreshToken")).toString())
                .body(response);
    }

    @Operation(summary = "Rotate refresh token and issue a new access token")
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestBody(required = false) RefreshRequest body,
            @CookieValue(value = REFRESH_COOKIE, required = false) String cookieToken) {

        // Accept the refresh token from the request body or, when omitted,
        // from the httpOnly cookie set at login.
        String token = body != null && StringUtils.hasText(body.refreshToken())
                ? body.refreshToken()
                : cookieToken;

        Map<String, Object> response = authService.refresh(new RefreshRequest(token));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        buildRefreshCookie((String) response.get("refreshToken")).toString())
                .body(response);
    }

    @Operation(summary = "Log out: revoke the access token and clear the refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring("Bearer ".length());
        }
        authService.logout(accessToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString())
                .body(Map.of("success", true, "message", "Logged out"));
    }

    private ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(refreshTokenValiditySeconds))
                .build();
    }

    private ResponseCookie clearedRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
    }
}
