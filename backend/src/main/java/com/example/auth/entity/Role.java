package com.example.auth.entity;

/**
 * Application roles. Stored with the Spring Security "ROLE_" prefix convention
 * so they map directly onto granted authorities.
 */
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}
