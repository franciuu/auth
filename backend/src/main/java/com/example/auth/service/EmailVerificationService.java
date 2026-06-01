package com.example.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Issues and consumes single-use email-verification tokens stored in Redis.
 * In a real deployment the link would be emailed; here it is logged so the
 * flow is fully exercisable without an SMTP server.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private static final String KEY_PREFIX = "ev:token:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.email.verification-ttl-hours:24}")
    private long ttlHours;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * Creates a verification token bound to the email and "sends" it.
     */
    public void sendVerificationToken(String email) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                KEY_PREFIX + token, email, Duration.ofHours(ttlHours));
        String link = frontendBaseUrl + "/verify-email?token=" + token;
        // Stand-in for an outbound email.
        log.info("EMAIL VERIFICATION for {} -> {}", email, link);
    }

    /**
     * Returns the email associated with the token and invalidates it, or null
     * if the token is unknown/expired.
     */
    public String consumeToken(String token) {
        String key = KEY_PREFIX + token;
        String email = redisTemplate.opsForValue().get(key);
        if (email != null) {
            redisTemplate.delete(key);
        }
        return email;
    }
}
