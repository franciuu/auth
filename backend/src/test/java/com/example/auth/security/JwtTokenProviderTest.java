package com.example.auth.security;

import com.example.auth.entity.Role;
import com.example.auth.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            "test-secret-key-that-is-definitely-long-enough-256bits!!",
            86400,
            604800);

    private User sampleUser() {
        return User.builder()
                .id(42L)
                .email("user@test.com")
                .fullName("Test User")
                .passwordHash("x")
                .roles(Set.of(Role.ROLE_USER))
                .build();
    }

    @Test
    void accessTokenCarriesEmailAndRoles() {
        String token = provider.generateAccessToken(sampleUser());
        Claims claims = provider.parse(token);

        assertNotNull(claims);
        assertTrue(provider.isAccessToken(claims));
        assertFalse(provider.isRefreshToken(claims));
        assertEquals("user@test.com", provider.getEmail(claims));
        assertTrue(provider.getRoles(claims).contains("ROLE_USER"));
        assertNotNull(provider.getJti(claims));
    }

    @Test
    void refreshTokenIsTypedRefresh() {
        String token = provider.generateRefreshToken(sampleUser());
        Claims claims = provider.parse(token);

        assertNotNull(claims);
        assertTrue(provider.isRefreshToken(claims));
        assertFalse(provider.isAccessToken(claims));
    }

    @Test
    void tamperedTokenFailsValidation() {
        String token = provider.generateAccessToken(sampleUser());
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertNull(provider.parse(tampered));
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtTokenProvider other = new JwtTokenProvider(
                "a-completely-different-secret-key-256bits-long-value!!", 86400, 604800);
        String foreignToken = other.generateAccessToken(sampleUser());
        assertNull(provider.parse(foreignToken));
    }

    @Test
    void shortSecretIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider("too-short", 86400, 604800));
    }
}
