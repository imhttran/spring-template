package com.example.template.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Pending 2FA logins ({@code login_codes}), keyed by the token handed to the client. */
@Repository
public class LoginCodeRepository {

    private final JdbcClient jdbc;

    public LoginCodeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Code(int userId, String code, OffsetDateTime expiresAt, boolean used, int attempts,
            String email) {
    }

    public record Resend(int resends, OffsetDateTime expiresAt, boolean used, String email) {
    }

    public void insert(int userId, String token, String code) {
        jdbc.sql("""
                INSERT INTO login_codes (user_id, token, code, expires_at)
                VALUES (:userId, :token, :code, now() + interval '10 minutes')
                """)
                .param("userId", userId)
                .param("token", token)
                .param("code", code)
                .update();
    }

    public Optional<Code> findByToken(String token) {
        return jdbc.sql("""
                SELECT lc.user_id AS "userId", lc.code, lc.expires_at AS "expiresAt",
                       lc.used, lc.attempts, u.email
                FROM login_codes lc
                JOIN users u ON u.id = lc.user_id
                WHERE lc.token = :token
                """)
                .param("token", token)
                .query(Code.class)
                .optional();
    }

    public Optional<Resend> findResendByToken(String token) {
        return jdbc.sql("""
                SELECT lc.resends, lc.expires_at AS "expiresAt", lc.used, u.email
                FROM login_codes lc
                JOIN users u ON u.id = lc.user_id
                WHERE lc.token = :token
                """)
                .param("token", token)
                .query(Resend.class)
                .optional();
    }

    /** Counts a wrong guess so a 4-digit code can't be brute-forced in its window. */
    public void incrementAttempts(String token) {
        jdbc.sql("UPDATE login_codes SET attempts = attempts + 1 WHERE token = :token")
                .param("token", token)
                .update();
    }

    public void markUsed(String token) {
        jdbc.sql("UPDATE login_codes SET used = true WHERE token = :token")
                .param("token", token)
                .update();
    }

    public void refreshCode(String token, String code) {
        jdbc.sql("""
                UPDATE login_codes
                SET code = :code, resends = resends + 1, expires_at = now() + interval '10 minutes'
                WHERE token = :token
                """)
                .param("code", code)
                .param("token", token)
                .update();
    }
}
