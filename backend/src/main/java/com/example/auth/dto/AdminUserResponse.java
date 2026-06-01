package com.example.auth.dto;

import com.example.auth.entity.Role;

import java.time.Instant;
import java.util.Set;

public record AdminUserResponse(
        Long id,
        String email,
        String fullName,
        Set<Role> roles,
        boolean active,
        Instant createdAt
) {
}
