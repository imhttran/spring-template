package com.example.template.service;

/**
 * A logged-in user, as both the auth gates and the route handlers need it.
 * {@code passwordHash} is the stored hash, checked by /api/change-password.
 */
public record AuthUser(
        int id,
        String email,
        String role,
        boolean emailVerified,
        boolean mustChangePassword,
        boolean hasProfile,
        String passwordHash) {
}
