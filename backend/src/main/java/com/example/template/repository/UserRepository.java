package com.example.template.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Every {@code users} query in the app. Raw SQL with {@link JdbcClient} —
 * column aliases spell out the camelCase shape the API returns.
 */
@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A logged-in user: everything the auth gates and /api/change-password need. */
    public record Account(
        int id,
        String email,
        String role,
        boolean emailVerified,
        boolean mustChangePassword,
        String password,
        boolean hasProfile
    ) {}

    public record LoginRow(int id, String password, boolean emailVerified) {}

    public record IdVerified(int id, boolean emailVerified) {}

    public record ResetRow(
        int id,
        String email,
        OffsetDateTime resetTokenExpiry
    ) {}

    public record VerificationRow(
        int id,
        String email,
        boolean emailVerified
    ) {}

    public record UserWithRole(
        int id,
        String email,
        String role,
        boolean emailVerified
    ) {}

    public record RoleRow(int id, String email, String role) {}

    public record ListItem(
        int id,
        String email,
        String role,
        boolean emailVerified,
        Instant createdAt
    ) {}

    public Optional<Account> findAccountByEmail(String email) {
        return jdbc
            .sql(
                """
                SELECT id, email, role,
                       email_verified       AS "emailVerified",
                       must_change_password AS "mustChangePassword",
                       password,
                       EXISTS (SELECT 1 FROM user_profiles WHERE user_id = users.id) AS "hasProfile"
                FROM users WHERE email = :email
                """
            )
            .param("email", email)
            .query(Account.class)
            .optional();
    }

    public Optional<LoginRow> findLoginRowByEmail(String email) {
        return jdbc
            .sql(
                """
                SELECT id, password, email_verified AS "emailVerified"
                FROM users WHERE email = :email
                """
            )
            .param("email", email)
            .query(LoginRow.class)
            .optional();
    }

    public Optional<IdVerified> findIdAndVerifiedByEmail(String email) {
        return jdbc
            .sql(
                """
                SELECT id, email_verified AS "emailVerified"
                FROM users WHERE email = :email
                """
            )
            .param("email", email)
            .query(IdVerified.class)
            .optional();
    }

    public Optional<Integer> findIdByEmail(String email) {
        return jdbc
            .sql("SELECT id FROM users WHERE email = :email")
            .param("email", email)
            .query(Integer.class)
            .optional();
    }

    public Optional<Integer> findIdByVerificationToken(String token) {
        return jdbc
            .sql("SELECT id FROM users WHERE verification_token = :token")
            .param("token", token)
            .query(Integer.class)
            .optional();
    }

    public Optional<ResetRow> findResetRowByToken(String token) {
        return jdbc
            .sql(
                """
                SELECT id, email, reset_token_expiry AS "resetTokenExpiry"
                FROM users WHERE reset_token = :token
                """
            )
            .param("token", token)
            .query(ResetRow.class)
            .optional();
    }

    public Optional<VerificationRow> findVerificationRowById(int id) {
        return jdbc
            .sql(
                """
                SELECT id, email, email_verified AS "emailVerified"
                FROM users WHERE id = :id
                """
            )
            .param("id", id)
            .query(VerificationRow.class)
            .optional();
    }

    public boolean existsByEmail(String email) {
        return Boolean.TRUE.equals(
            jdbc
                .sql("SELECT EXISTS (SELECT 1 FROM users WHERE email = :email)")
                .param("email", email)
                .query(Boolean.class)
                .single()
        );
    }

    /** Signup: unverified, carrying the verification token the email links to. */
    public int insertUser(
        String email,
        String passwordHash,
        String verificationToken
    ) {
        return jdbc
            .sql(
                """
                INSERT INTO users (email, password, email_verified, verification_token)
                VALUES (:email, :password, false, :token)
                RETURNING id
                """
            )
            .param("email", email)
            .param("password", passwordHash)
            .param("token", verificationToken)
            .query(Integer.class)
            .single();
    }

    /** Dev seed: keeps whatever is already there rather than overwriting it. */
    public Optional<Integer> insertUserIfAbsent(
        String email,
        String passwordHash,
        String role,
        boolean emailVerified
    ) {
        return jdbc
            .sql(
                """
                INSERT INTO users (email, password, role, email_verified)
                VALUES (:email, :password, :role, :verified)
                ON CONFLICT (email) DO NOTHING
                RETURNING id
                """
            )
            .param("email", email)
            .param("password", passwordHash)
            .param("role", role)
            .param("verified", emailVerified)
            .query(Integer.class)
            .optional();
    }

    /**
     * Admin-created accounts arrive already verified (the admin vouches for the
     * email) and flagged to force a password change on first login.
     */
    public UserWithRole insertAdminCreatedUser(
        String email,
        String passwordHash
    ) {
        return jdbc
            .sql(
                """
                INSERT INTO users (email, password, email_verified, must_change_password)
                VALUES (:email, :password, true, true)
                RETURNING id, email, role, email_verified AS "emailVerified"
                """
            )
            .param("email", email)
            .param("password", passwordHash)
            .query(UserWithRole.class)
            .single();
    }

    public void markEmailVerified(int id) {
        jdbc.sql(
            "UPDATE users SET email_verified = true, verification_token = NULL WHERE id = :id"
        )
            .param("id", id)
            .update();
    }

    public void updatePassword(int id, String passwordHash) {
        jdbc.sql(
            """
            UPDATE users
            SET password = :password, must_change_password = false
            WHERE id = :id
            """
        )
            .param("password", passwordHash)
            .param("id", id)
            .update();
    }

    public void applyPasswordReset(int id, String passwordHash) {
        jdbc.sql(
            """
            UPDATE users
            SET password = :password, reset_token = NULL, reset_token_expiry = NULL,
                must_change_password = false
            WHERE id = :id
            """
        )
            .param("password", passwordHash)
            .param("id", id)
            .update();
    }

    public void setVerificationToken(int id, String token) {
        jdbc.sql("UPDATE users SET verification_token = :token WHERE id = :id")
            .param("token", token)
            .param("id", id)
            .update();
    }

    /** Returns the email the reset token was set on, or empty when nothing matched. */
    public Optional<String> setResetTokenByEmail(
        String email,
        String token,
        OffsetDateTime expiry
    ) {
        return jdbc
            .sql(
                """
                UPDATE users SET reset_token = :token, reset_token_expiry = :expiry
                WHERE email = :email
                RETURNING email
                """
            )
            .param("token", token)
            .param("expiry", expiry)
            .param("email", email)
            .query(String.class)
            .optional();
    }

    public Optional<String> setResetTokenById(
        int id,
        String token,
        OffsetDateTime expiry
    ) {
        return jdbc
            .sql(
                """
                UPDATE users SET reset_token = :token, reset_token_expiry = :expiry
                WHERE id = :id
                RETURNING email
                """
            )
            .param("token", token)
            .param("expiry", expiry)
            .param("id", id)
            .query(String.class)
            .optional();
    }

    public void updateRoleByEmail(String email, String role) {
        jdbc.sql("UPDATE users SET role = :role WHERE email = :email")
            .param("role", role)
            .param("email", email)
            .update();
    }

    public Optional<VerificationRow> updateVerification(
        int id,
        boolean verified
    ) {
        return jdbc
            .sql(
                """
                UPDATE users SET email_verified = :verified, verification_token = NULL
                WHERE id = :id
                RETURNING id, email, email_verified AS "emailVerified"
                """
            )
            .param("verified", verified)
            .param("id", id)
            .query(VerificationRow.class)
            .optional();
    }

    public Optional<RoleRow> updateRole(int id, String role) {
        return jdbc
            .sql(
                """
                UPDATE users SET role = :role WHERE id = :id
                RETURNING id, email, role
                """
            )
            .param("role", role)
            .param("id", id)
            .query(RoleRow.class)
            .optional();
    }

    /** @return rows deleted (0 → no such user). */
    public int deleteById(int id) {
        return jdbc
            .sql("DELETE FROM users WHERE id = :id")
            .param("id", id)
            .update();
    }

    /**
     * Staff see clients and other staff — admin accounts aren't theirs to
     * manage. Admin sees everyone.
     */
    public List<ListItem> list(boolean includeAdminAccounts) {
        String filter = includeAdminAccounts
            ? ""
            : " WHERE role IN ('client', 'staff')";
        // Explicit row mapper so created_at is normalised to UTC before it is
        // serialised, whatever offset the driver hands back.
        return jdbc
            .sql(
                """
                SELECT id, email, role,
                       email_verified AS "emailVerified",
                       created_at     AS "createdAt"
                FROM users%s
                ORDER BY created_at ASC
                """.formatted(filter)
            )
            .query((rs, rowNum) ->
                new ListItem(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("role"),
                    rs.getBoolean("emailVerified"),
                    rs.getObject("createdAt", OffsetDateTime.class).toInstant()
                )
            )
            .list();
    }
}
