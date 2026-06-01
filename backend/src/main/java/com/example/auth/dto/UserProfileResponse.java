package com.example.auth.dto;

import com.example.auth.entity.Role;

import java.util.Set;

public record UserProfileResponse(
        Long id,
        String email,
        String fullName,
        Set<Role> roles
) {
}
