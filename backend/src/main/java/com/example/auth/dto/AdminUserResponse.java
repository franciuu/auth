package com.example.auth.dto;

import com.example.auth.model.Role;

import java.time.Instant;
import java.util.Set;

/**
 * Response DTO for the admin "list users" view.
 */
public record AdminUserResponse(
        Long id,
        String email,
        String fullName,
        Set<Role> roles,
        boolean active,
        Instant createdAt
) {
}
