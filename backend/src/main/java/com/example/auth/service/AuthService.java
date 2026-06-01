package com.example.auth.service;

import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RefreshRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.exception.AuthException;
import com.example.auth.model.AuditEventType;
import com.example.auth.model.Role;
import com.example.auth.model.Severity;
import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Core authentication flows: registration, login, refresh-with-rotation, logout.
 * All client-facing failures use generic messages to prevent user enumeration.
 *
 * <p>Login and refresh return a plain map of token data (accessToken,
 * refreshToken, expiresIn) - kept deliberately simple since DTOs here are used
 * only for incoming requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /** Generic credential error - identical for unknown email and wrong password. */
    private static final String GENERIC_CREDENTIALS_ERROR = "Invalid credentials";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuditService auditService;

    @Transactional
    public void register(RegisterRequest request) {
        String email = normalize(request.email());

        // Pre-hash so registration timing/response is identical whether or not
        // the email already exists (anti-enumeration). We still avoid creating
        // a duplicate, but the client always sees the same generic success.
        String passwordHash = passwordEncoder.encode(request.password());

        if (userRepository.existsByEmail(email)) {
            // Do not reveal that the account exists.
            log.info("Registration attempt for already-registered email (masked)");
            auditService.record(AuditEventType.USER_REGISTERED, null, Severity.INFO,
                    "Registration attempt for an existing email");
            return;
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .fullName(request.fullName().trim())
                .active(true)
                .roles(Set.of(Role.ROLE_USER))
                .build();
        User saved = userRepository.save(user);

        auditService.record(AuditEventType.USER_REGISTERED, saved.getId(), Severity.INFO,
                "New user registered");
    }

    @Transactional
    public Map<String, Object> login(LoginRequest request) {
        String email = normalize(request.email());

        User user = userRepository.findByEmail(email).orElse(null);

        boolean passwordMatches = user != null
                && passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (user == null || !passwordMatches) {
            auditService.record(AuditEventType.FAILED_LOGIN,
                    user != null ? user.getId() : null, Severity.WARN,
                    "Failed login attempt");
            throw new AuthException(HttpStatus.UNAUTHORIZED, GENERIC_CREDENTIALS_ERROR);
        }

        if (!user.isActive()) {
            auditService.record(AuditEventType.FAILED_LOGIN, user.getId(), Severity.WARN,
                    "Login attempt on disabled account");
            // Generic message - do not disclose the account is disabled.
            throw new AuthException(HttpStatus.UNAUTHORIZED, GENERIC_CREDENTIALS_ERROR);
        }

        return issueTokens(user, AuditEventType.LOGIN_SUCCESS, "Successful login");
    }

    @Transactional
    public Map<String, Object> refresh(RefreshRequest request) {
        Claims claims = tokenProvider.parse(request.refreshToken());
        if (claims == null || !tokenProvider.isRefreshToken(claims)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        String email = tokenProvider.getEmail(claims);
        User user = userRepository.findByEmail(email).orElse(null);

        // One-time use: the presented token must match the currently stored one.
        if (user == null || !user.isActive()
                || !request.refreshToken().equals(user.getRefreshToken())) {
            // A mismatch may indicate a reused/stolen token: revoke the chain.
            if (user != null) {
                user.setRefreshToken(null);
                userRepository.save(user);
            }
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        return issueTokens(user, AuditEventType.TOKEN_REFRESHED, "Refresh token rotated");
    }

    @Transactional
    public void logout(String accessToken) {
        if (accessToken == null) {
            return;
        }
        Claims claims = tokenProvider.parse(accessToken);
        if (claims == null) {
            return;
        }
        // Blacklist the access token until its natural expiry.
        tokenBlacklistService.blacklist(
                tokenProvider.getJti(claims), tokenProvider.getExpiration(claims));

        // Invalidate the refresh token so it cannot be rotated further.
        userRepository.findByEmail(tokenProvider.getEmail(claims)).ifPresent(user -> {
            Long uid = user.getId();
            user.setRefreshToken(null);
            userRepository.save(user);
            auditService.record(AuditEventType.LOGOUT, uid, Severity.INFO, "User logged out");
        });
    }

    /** Issues a fresh access+refresh pair and persists the new refresh token. */
    private Map<String, Object> issueTokens(User user, AuditEventType event, String auditMessage) {
        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        auditService.record(event, user.getId(), Severity.INFO, auditMessage);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("expiresIn", tokenProvider.getAccessTokenValiditySeconds());
        return response;
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
