package com.example.auth.dto;

import com.example.auth.model.Role;

import java.util.Set;

/**
 * Response DTO for the current user's own profile.
 */
public record UserProfileResponse(
        Long id,
        String email,
        String fullName,
        Set<Role> roles
) {
}
