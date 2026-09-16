package com.example.template.service;

import com.example.template.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * HS256 JWTs carrying the {@code {email, exp, iat}} claims the API contract
 * pins.
 *
 * <p>Sessions expire {@link #SESSION_TTL_SECONDS} after issue; the filter
 * slides active sessions forward by re-issuing past the half-life.
 */
@Service
public class JwtService {

    public static final long SESSION_TTL_SECONDS = 600; // 10 minutes
    private static final long RENEW_THRESHOLD_SECONDS = 300; // half-life
    private static final int MINIMUM_SECRET_BYTES = 32; // 256 bits, HS256's key size

    private final SecretKey key;

    public JwtService(AppProperties properties) {
        byte[] secret = properties
            .getJwtSecret()
            .getBytes(StandardCharsets.UTF_8);
        // Refuse a weak key up front rather than failing deep inside the
        // signer: a short HMAC secret is brute-forceable.
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least " +
                    MINIMUM_SECRET_BYTES +
                    " bytes for HS256"
            );
        }
        this.key = new SecretKeySpec(secret, "HmacSHA256");
    }

    public String issue(String email) {
        return issueWithTtl(email, SESSION_TTL_SECONDS);
    }

    public String issueWithTtl(String email, long ttlSeconds) {
        long now = Instant.now().getEpochSecond();
        return Jwts.builder()
            .claim("email", email)
            .issuedAt(Date.from(Instant.ofEpochSecond(now)))
            .expiration(Date.from(Instant.ofEpochSecond(now + ttlSeconds)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    /**
     * @return the email claim, or empty on any failure — bad signature, expired,
     *         malformed, or a token whose email claim is missing/blank.
     */
    public Optional<String> verify(String token) {
        return claims(token)
            .map(claims -> claims.get("email", String.class))
            .filter(email -> email != null && !email.isEmpty());
    }

    /**
     * A fresh token when the current one is past its half-life, so an active
     * user's session slides forward instead of hard-expiring mid-use. Expired
     * or invalid tokens are never renewed.
     */
    public String renewIfDue(String token) {
        return claims(token)
            .filter(claims -> claims.getExpiration() != null)
            .filter(
                claims ->
                    claims.getExpiration().toInstant().getEpochSecond() -
                        Instant.now().getEpochSecond() <
                    RENEW_THRESHOLD_SECONDS
            )
            .map(claims -> issue(claims.get("email", String.class)))
            .orElse(null);
    }

    private Optional<Claims> claims(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
            );
        } catch (JwtException | IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }
}
