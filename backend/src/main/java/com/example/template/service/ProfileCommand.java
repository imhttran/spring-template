package com.example.template.service;

/**
 * The registration form as it arrived, before validation and trimming. Blank
 * optionals are stored as NULL.
 */
public record ProfileCommand(
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
        String altEmail) {
}
