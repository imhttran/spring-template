package com.example.template.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.template.config.AppProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-long-enough-for-hs256";

    private final JwtService jwt = new JwtService(properties(SECRET));

    @Test
    void jwtRoundTrip() {
        String token = jwt.issue("a@b.c");
        assertEquals(Optional.of("a@b.c"), jwt.verify(token));
    }

    @Test
    void rejectsAnotherSecret() {
        String token = jwt.issue("a@b.c");
        assertNull(
            new JwtService(properties("a-different-secret-that-is-long-enough"))
                .verify(token)
                .orElse(null)
        );
    }

    @Test
    void rejectsGarbage() {
        assertEquals(Optional.empty(), jwt.verify("not-a-jwt"));
        assertEquals(Optional.empty(), jwt.verify(""));
    }

    @Test
    void rejectsExpiredTokens() {
        assertEquals(
            Optional.empty(),
            jwt.verify(jwt.issueWithTtl("a@b.c", -60))
        );
    }

    @Test
    void ignoresTokensWithoutAnEmailClaim() {
        assertEquals(Optional.empty(), jwt.verify(jwt.issueWithTtl("", 600)));
    }

    @Test
    void renewalOnlyPastHalfLife() {
        // A fresh (full-life) token is not renewed...
        assertNull(jwt.renewIfDue(jwt.issue("a@b.c")));
        // ...one deep into its life is...
        String renewed = jwt.renewIfDue(jwt.issueWithTtl("a@b.c", 60));
        assertNotNull(renewed);
        assertEquals(Optional.of("a@b.c"), jwt.verify(renewed));
        // ...and an expired one is dead, not renewable.
        assertNull(jwt.renewIfDue(jwt.issueWithTtl("a@b.c", -60)));
        assertNull(jwt.renewIfDue("not-a-jwt"));
    }

    /** Short HMAC secrets are brute-forceable, so they're refused up front. */
    @Test
    void refusesSecretsShorterThanTheHmacKeySize() {
        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> new JwtService(properties("short"))
        );
        assertTrue(failure.getMessage().contains("JWT_SECRET"));
    }

    private static AppProperties properties(String secret) {
        AppProperties properties = new AppProperties();
        properties.setJwtSecret(secret);
        return properties;
    }
}
