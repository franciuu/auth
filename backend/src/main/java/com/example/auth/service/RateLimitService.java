package com.example.auth.service;

import com.example.auth.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed sliding-window rate limiter for login attempts.
 * Allows {@code maxAttempts} failures within {@code windowMinutes}; on exceed,
 * further attempts are rejected with HTTP 429 until the window expires.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private static final String KEY_PREFIX = "rl:login:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.rate-limit.window-minutes:15}")
    private long windowMinutes;

    /**
     * Throws {@link RateLimitExceededException} if the key has already exceeded
     * the allowed number of attempts. Call this BEFORE attempting authentication.
     */
    public void checkAllowed(String key) {
        String redisKey = KEY_PREFIX + key;
        String value = redisTemplate.opsForValue().get(redisKey);
        if (value != null && Integer.parseInt(value) >= maxAttempts) {
            throw new RateLimitExceededException(
                    "Too many login attempts. Please try again later.");
        }
    }

    /**
     * Record a failed attempt and (re)set the window TTL on first failure.
     */
    public void recordFailure(String key) {
        String redisKey = KEY_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, Duration.ofMinutes(windowMinutes));
        }
    }

    /**
     * Clear the counter after a successful login.
     */
    public void reset(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }
}
