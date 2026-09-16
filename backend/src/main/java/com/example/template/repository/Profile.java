package com.example.template.repository;

/**
 * A row of {@code user_profiles}. Field names match the Postgres columns; the
 * record's camelCase component names produce the wire format. Nullable columns
 * are nullable references.
 */
public record Profile(
    int id,
    int userId,
    String firstName,
    String lastName,
    String address,
    String address2,
    String state,
    String zip,
    String country,
    String phone,
    String communicationPreference,
    String linkedin,
    String github,
    String altEmail
) {}
