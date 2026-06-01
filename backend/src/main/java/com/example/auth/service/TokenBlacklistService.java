package com.example.auth.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of access tokens that have been revoked via logout.
 *
 * <p>This is an in-memory store (a concurrent map of token id -> expiry). It is
 * intentionally simple for learning purposes; in a multi-instance production
 * deployment you would use a shared store (e.g. Redis) instead. Expired entries
 * are pruned lazily on lookup so the map does not grow unbounded.
 */
@Service
public class TokenBlacklistService {

    private final Map<String, Instant> blacklisted = new ConcurrentHashMap<>();

    /** Revoke a token by its id (jti) until its natural expiry. */
    public void blacklist(String jti, Instant expiresAt) {
        if (jti != null) {
            blacklisted.put(jti, expiresAt);
        }
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        Instant expiry = blacklisted.get(jti);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            blacklisted.remove(jti); // already expired; clean up
            return false;
        }
        return true;
    }
}
