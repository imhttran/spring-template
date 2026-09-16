package com.example.template.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void passwordRoundTrip() {
        String stored = hasher.hash("Valid123!");
        assertTrue(hasher.verify("Valid123!", stored));
        assertFalse(hasher.verify("Wrong123!", stored));
    }

    /**
     * A hash created by the Go backend (the dev-admin seed). If this ever fails,
     * the Node-compatible scrypt format has drifted.
     */
    @Test
    void verifiesHashesCreatedByTheGoBackend() {
        String stored = "1b3720e73189cbc4c90595519584e629"
                + ":b9d00978ebbb9477a6751a63c02933922146945a21ae6ee7c25d012cb3350917"
                + "4daef57c7f782cfed8f811c4af52a6cc07ff476fccf2f3fe1b7abe880211772d";
        assertTrue(hasher.verify("Password1234!", stored));
        assertFalse(hasher.verify("Password12345", stored));
    }

    @Test
    void rejectsGarbage() {
        assertFalse(hasher.verify("x", "no-colon"));
        assertFalse(hasher.verify("x", "salt:not-hex"));
        assertFalse(hasher.verify("x", "salt:"));
        assertFalse(hasher.verify("x", ""));
    }

    @Test
    void eachHashGetsItsOwnSalt() {
        assertFalse(hasher.hash("Valid123!").equals(hasher.hash("Valid123!")));
    }
}
