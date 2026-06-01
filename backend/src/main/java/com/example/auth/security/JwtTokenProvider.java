package com.example.auth.security;

import com.example.auth.model.Role;
import com.example.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and validates JWT access and refresh tokens.
 * Access tokens carry roles and are short(er)-lived; refresh tokens are opaque
 * to authorities and longer-lived. Both are signed with HMAC-SHA256.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ROLES_CLAIM = "roles";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey signingKey;

    /** Access token validity in seconds (default 24h). */
    private final long accessTokenValiditySeconds;

    /** Refresh token validity in seconds (default 7 days). */
    private final long refreshTokenValiditySeconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-validity-seconds:86400}") long accessTokenValiditySeconds,
            @Value("${app.jwt.refresh-token-validity-seconds:604800}") long refreshTokenValiditySeconds) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 256 bits (32 bytes) for HMAC-SHA256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenValiditySeconds, ChronoUnit.SECONDS);
        List<String> roles = user.getRoles().stream().map(Role::name).toList();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim(TOKEN_TYPE_CLAIM, ACCESS)
                .claim(ROLES_CLAIM, roles)
                .claim("uid", user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(refreshTokenValiditySeconds, ChronoUnit.SECONDS);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getEmail())
                .claim(TOKEN_TYPE_CLAIM, REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and verifies signature + expiration. Returns null when invalid.
     */
    public Claims parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return jws.getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return null;
        }
    }

    public boolean isAccessToken(Claims claims) {
        return claims != null && ACCESS.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return claims != null && REFRESH.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public String getEmail(Claims claims) {
        return claims.getSubject();
    }

    public String getJti(Claims claims) {
        return claims.getId();
    }

    public Instant getExpiration(Claims claims) {
        return claims.getExpiration().toInstant();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        Object roles = claims.get(ROLES_CLAIM);
        return roles instanceof List ? (List<String>) roles : List.of();
    }
}
