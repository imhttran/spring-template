package com.example.template.service;

import com.example.template.config.AppProperties;
import com.example.template.repository.DeviceRepository;
import com.example.template.repository.EmailQueueRepository;
import com.example.template.repository.LoginCodeRepository;
import com.example.template.repository.UserRepository;
import com.example.template.service.EmailQueueService.ResetKey;
import com.example.template.service.error.ConflictException;
import com.example.template.service.error.ForbiddenException;
import com.example.template.service.error.NotFoundException;
import com.example.template.service.error.ServerErrorException;
import com.example.template.service.error.TooManyRequestsException;
import com.example.template.service.error.UnauthenticatedException;
import com.example.template.service.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Signup, verification, password reset, login (with 2FA), and self-service password change. */
@Service
public class AuthService {

    /** Both shapes a successful login can take. */
    public sealed interface LoginResult {
        record Authenticated(
            String email,
            String token
        ) implements LoginResult {}

        /** A 2FA code was emailed; the real token comes from /api/login/verify. */
        record TwoFactorRequired(String pendingToken) implements LoginResult {}
    }

    public record PasswordReset(String email, String token) {}

    private static final Logger log = LoggerFactory.getLogger(
        AuthService.class
    );

    /** Wrong guesses on a 4-digit code before the pending login is locked. */
    private static final int MAX_CODE_ATTEMPTS = 5;

    /** Resends allowed per pending login. */
    private static final int MAX_CODE_RESENDS = 3;

    private final UserRepository users;
    private final DeviceRepository devices;
    private final LoginCodeRepository loginCodes;
    private final EmailQueueRepository emailQueue;
    private final EmailQueueService queuedEmails;
    private final PasswordHasher hasher;
    private final Tokens tokens;
    private final JwtService jwt;
    private final AppProperties properties;
    private final TransactionTemplate transactions;

    public AuthService(
        UserRepository users,
        DeviceRepository devices,
        LoginCodeRepository loginCodes,
        EmailQueueRepository emailQueue,
        EmailQueueService queuedEmails,
        PasswordHasher hasher,
        Tokens tokens,
        JwtService jwt,
        AppProperties properties,
        TransactionTemplate transactions
    ) {
        this.users = users;
        this.devices = devices;
        this.loginCodes = loginCodes;
        this.emailQueue = emailQueue;
        this.queuedEmails = queuedEmails;
        this.hasher = hasher;
        this.tokens = tokens;
        this.jwt = jwt;
        this.properties = properties;
        this.transactions = transactions;
    }

    /**
     * Token verification, user lookup and the email-verification gate — what
     * declaring an {@code AuthUser} parameter requires. The onboarding gates
     * are applied later, by the resolver, because they depend on the route
     * being called.
     */
    public AuthUser authenticate(String token) {
        Optional<String> email = jwt.verify(token);
        if (email.isEmpty()) {
            throw new ForbiddenException("Invalid or expired token");
        }
        UserRepository.Account account;
        try {
            account = users
                .findAccountByEmail(email.get())
                .orElseThrow(() -> new NotFoundException("User not found"));
        } catch (DataAccessException failed) {
            // The user lookup sits in the same try/catch as the JWT check, so
            // any failure here reads as a bad token.
            throw new ForbiddenException("Invalid or expired token");
        }
        if (
            properties.isEmailVerificationRequired() && !account.emailVerified()
        ) {
            throw new ForbiddenException("Please verify your email");
        }
        return new AuthUser(
            account.id(),
            account.email(),
            account.role(),
            account.emailVerified(),
            account.mustChangePassword(),
            account.hasProfile(),
            account.password()
        );
    }

    public void signup(String email, String password) {
        if (!Validators.isEmail(email)) {
            throw new ValidationException("Invalid email address");
        }
        String passwordError = Validators.validatePassword(password);
        if (passwordError != null) {
            throw new ValidationException(passwordError);
        }

        // Atomic: user + welcome-email row + verification-email row together, so
        // a failed email never leaves an orphaned account and a rolled-back
        // signup leaves no queued email. The send itself is deferred to the
        // worker — signup is never blocked on mail delivery.
        String verificationToken = tokens.randomToken();
        String passwordHash = hasher.hash(password);
        try {
            transactions.executeWithoutResult(status -> {
                users.insertUser(email, passwordHash, verificationToken);
                enqueue(EmailTemplates.welcome(email));
                enqueue(
                    EmailTemplates.verification(
                        email,
                        EmailTemplates.tokenLink(
                            properties.getFrontendUrl(),
                            "verify",
                            verificationToken
                        )
                    )
                );
            });
        } catch (DuplicateKeyException alreadyRegistered) {
            // Keep the user-facing message generic so the API doesn't reveal
            // whether an email is already registered (prevents user
            // enumeration). The real reason is logged server-side.
            log.warn(
                "[signup] rejected: email already registered (email={})",
                email
            );
            throw new ConflictException(
                "Unable to sign up. Please try again later."
            );
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Signup Error", failed, true);
        }
    }

    public void verify(String token) {
        try {
            if (token.isEmpty()) {
                throw new ValidationException("Missing verification token");
            }
            Integer id = users.findIdByVerificationToken(token).orElse(null);
            if (id == null) {
                throw new ValidationException(
                    "Invalid or expired verification link"
                );
            }
            users.markEmailVerified(id);
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Verify Error", failed, true);
        }
    }

    public void resendVerification(String email) {
        if (!Validators.isEmail(email)) {
            throw new ValidationException("Invalid email address");
        }
        UserRepository.IdVerified row;
        try {
            row = users.findIdAndVerifiedByEmail(email).orElse(null);
        } catch (DataAccessException failed) {
            row = null;
        }
        if (row != null && !row.emailVerified()) {
            try {
                queuedEmails.queueVerificationEmail(row.id(), email);
            } catch (DataAccessException failed) {
                throw new ServerErrorException(
                    "Resend Verification Error",
                    failed,
                    true
                );
            }
        }
        // Same response whether or not the account exists or is verified, so
        // this endpoint can't be used to enumerate registered emails.
    }

    public void forgotPassword(String email) {
        if (!Validators.isEmail(email)) {
            throw new ValidationException("Invalid email address");
        }
        try {
            queuedEmails.queuePasswordReset(new ResetKey.ByEmail(email));
        } catch (NotFoundException noSuchUser) {
            // Fall through to the generic response.
        } catch (DataAccessException failed) {
            throw new ServerErrorException(
                "Forgot Password Error",
                failed,
                true
            );
        }
    }

    public PasswordReset resetPassword(String token, String password) {
        if (token.isEmpty()) {
            throw new ValidationException("Missing reset token");
        }
        String passwordError = Validators.validatePassword(password);
        if (passwordError != null) {
            throw new ValidationException(passwordError);
        }
        UserRepository.ResetRow row;
        try {
            row = users.findResetRowByToken(token).orElse(null);
        } catch (DataAccessException failed) {
            // Any failure to find a usable unexpired token reads the same.
            row = null;
        }
        if (
            row == null ||
            row.resetTokenExpiry() == null ||
            row.resetTokenExpiry().toInstant().isBefore(Instant.now())
        ) {
            throw new ValidationException("Invalid or expired reset link");
        }
        try {
            users.applyPasswordReset(row.id(), hasher.hash(password));
        } catch (DataAccessException failed) {
            throw new ServerErrorException(
                "Reset Password Error",
                failed,
                true
            );
        }
        return new PasswordReset(row.email(), jwt.issue(row.email()));
    }

    public LoginResult login(String email, String password, String deviceId) {
        UserRepository.LoginRow row;
        try {
            row = users.findLoginRowByEmail(email).orElse(null);
        } catch (DataAccessException failed) {
            // Bad credentials and lookup failures read the same.
            row = null;
        }
        if (row == null || !hasher.verify(password, row.password())) {
            throw new UnauthenticatedException("Invalid email or password");
        }
        if (properties.isEmailVerificationRequired() && !row.emailVerified()) {
            throw new ForbiddenException(
                "Please verify your email before logging in."
            );
        }
        if (!deviceId.isEmpty() && isTrustedDevice(row.id(), deviceId)) {
            return new LoginResult.Authenticated(email, jwt.issue(email));
        }

        // New device — require 2FA: queue an emailed code and hand back a
        // pending token. The real JWT is only issued by /api/login/verify.
        String pending = tokens.randomToken();
        String code = tokens.randomCode();
        try {
            loginCodes.insert(row.id(), pending, code);
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Login Error", failed, false);
        }
        sendLoginCode(email, code);
        return new LoginResult.TwoFactorRequired(pending);
    }

    public LoginResult verifyLogin(String token, String code, String deviceId) {
        LoginCodeRepository.Code row;
        try {
            row = loginCodes
                .findByToken(token)
                .orElseThrow(() ->
                    new ValidationException("Invalid or expired code")
                );
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Verify Login Error", failed, false);
        }
        // Lock the code after a handful of failed tries so a 4-digit code can't
        // be brute-forced within its 10-minute window.
        if (
            row.used() ||
            Instant.now().isAfter(row.expiresAt().toInstant()) ||
            row.attempts() >= MAX_CODE_ATTEMPTS
        ) {
            throw new ValidationException("Invalid or expired code");
        }
        if (!constantTimeEquals(row.code(), code)) {
            try {
                loginCodes.incrementAttempts(token);
            } catch (DataAccessException ignored) {
                // A failed attempt counter is not worth failing the request over.
            }
            throw new ValidationException("Invalid or expired code");
        }
        try {
            loginCodes.markUsed(token);
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Verify Login Error", failed, false);
        }
        if (!deviceId.isEmpty()) {
            try {
                devices.trust(row.userId(), deviceId);
            } catch (DataAccessException ignored) {
                // Registering the device is best effort.
            }
        }
        return new LoginResult.Authenticated(
            row.email(),
            jwt.issue(row.email())
        );
    }

    public void resendLoginCode(String token) {
        LoginCodeRepository.Resend row;
        try {
            row = loginCodes.findResendByToken(token).orElse(null);
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Resend Code Error", failed, false);
        }
        if (
            row == null ||
            row.used() ||
            Instant.now().isAfter(row.expiresAt().toInstant())
        ) {
            throw new ValidationException("Invalid or expired code");
        }
        if (row.resends() >= MAX_CODE_RESENDS) {
            throw new TooManyRequestsException("Too many resend attempts");
        }
        String code = tokens.randomCode();
        try {
            loginCodes.refreshCode(token, code);
        } catch (DataAccessException failed) {
            throw new ServerErrorException("Resend Code Error", failed, false);
        }
        sendLoginCode(row.email(), code);
    }

    /**
     * Authenticated self-service password change. Used both for the general
     * "change my password" case and to clear the forced-change flag an
     * admin-created account starts with.
     */
    public void changePassword(
        int userId,
        String storedHash,
        String currentPassword,
        String newPassword
    ) {
        if (!hasher.verify(currentPassword, storedHash)) {
            throw new UnauthenticatedException("Current password is incorrect");
        }
        String passwordError = Validators.validatePassword(newPassword);
        if (passwordError != null) {
            throw new ValidationException(passwordError);
        }
        try {
            users.updatePassword(userId, hasher.hash(newPassword));
        } catch (DataAccessException failed) {
            throw new ServerErrorException(
                "Change Password Error",
                failed,
                false
            );
        }
    }

    private boolean isTrustedDevice(int userId, String deviceId) {
        try {
            return devices.isTrusted(userId, deviceId);
        } catch (DataAccessException failed) {
            return false;
        }
    }

    /** Emails the 2FA code through the shared queue; the worker delivers it. */
    private void sendLoginCode(String email, String code) {
        try {
            enqueue(EmailTemplates.loginCode(email, code));
        } catch (DataAccessException ignored) {
            // The pending login still works; the code is logged by the mailer in dev.
        }
    }

    private void enqueue(EmailTemplates.Email message) {
        emailQueue.enqueue(message.to(), message.subject(), message.body());
    }

    private static boolean constantTimeEquals(
        String expected,
        String submitted
    ) {
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            submitted.getBytes(StandardCharsets.UTF_8)
        );
    }
}
