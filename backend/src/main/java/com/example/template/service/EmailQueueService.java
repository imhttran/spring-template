package com.example.template.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.template.config.AppProperties;
import com.example.template.repository.EmailQueueRepository;
import com.example.template.repository.UserRepository;
import com.example.template.service.error.NotFoundException;

/**
 * Writes to the outbound mail queue. Each enqueue happens in the same
 * transaction as the token change that caused it, so a rolled-back request
 * leaves no queued email — and no request ever waits for a mail server.
 */
@Service
public class EmailQueueService {

    /** Prisma's P2025 (record not found in an update) as a sentinel, as before. */
    public sealed interface ResetKey {

        record ByEmail(String email) implements ResetKey {
        }

        record ById(int id) implements ResetKey {
        }
    }

    private static final Logger log = LoggerFactory.getLogger(EmailQueueService.class);

    private static final int RESET_TOKEN_TTL_HOURS = 1;

    private final UserRepository users;
    private final EmailQueueRepository queue;
    private final Mailer mailer;
    private final Tokens tokens;
    private final AppProperties properties;
    private final TransactionTemplate transactions;

    public EmailQueueService(UserRepository users, EmailQueueRepository queue, Mailer mailer, Tokens tokens,
            AppProperties properties, TransactionTemplate transactions) {
        this.users = users;
        this.queue = queue;
        this.mailer = mailer;
        this.tokens = tokens;
        this.properties = properties;
        this.transactions = transactions;
    }

    /**
     * Shared by /api/forgot-password and the admin-triggered reset route. The
     * update itself both finds the user and sets the token, so callers don't
     * need their own lookup.
     *
     * @throws NotFoundException when no user matches — self-service forgot-password
     *         ignores it, the admin route turns it into a 404.
     */
    public void queuePasswordReset(ResetKey key) {
        String token = tokens.randomToken();
        OffsetDateTime expiry = OffsetDateTime.now(ZoneOffset.UTC).plusHours(RESET_TOKEN_TTL_HOURS);
        transactions.executeWithoutResult(status -> {
            Optional<String> matched = switch (key) {
                case ResetKey.ByEmail(String email) -> users.setResetTokenByEmail(email, token, expiry);
                case ResetKey.ById(int id) -> users.setResetTokenById(id, token, expiry);
            };
            if (matched.isEmpty()) {
                throw new NotFoundException("user not found");
            }
            String email = matched.get();
            EmailTemplates.Email message = EmailTemplates.passwordReset(email,
                    EmailTemplates.tokenLink(properties.getFrontendUrl(), "reset-password", token));
            queue.enqueue(message.to(), message.subject(), message.body());
        });
    }

    /** Shared by /api/resend-verification and the staff-triggered resend route. */
    public void queueVerificationEmail(int userId, String email) {
        String token = tokens.randomToken();
        transactions.executeWithoutResult(status -> {
            users.setVerificationToken(userId, token);
            EmailTemplates.Email message = EmailTemplates.verification(email,
                    EmailTemplates.tokenLink(properties.getFrontendUrl(), "verify", token));
            queue.enqueue(message.to(), message.subject(), message.body());
        });
    }

    /**
     * Pick up pending emails, send them, mark sent / retry with a bounded cap.
     *
     * @return the number of jobs processed (used by tests).
     */
    public int process(int take) {
        List<EmailQueueRepository.Job> jobs;
        try {
            jobs = queue.findPending(properties.getMaxAttempts(), take);
        } catch (Exception failed) {
            log.error("[emailQueue] worker error: {}", failed.getMessage());
            return 0;
        }
        for (EmailQueueRepository.Job job : jobs) {
            try {
                mailer.send(job.to(), job.subject(), job.body());
                queue.markSent(job.id());
            } catch (MailException failed) {
                int attempts = job.attempts() + 1;
                String status = attempts >= properties.getMaxAttempts() ? "failed" : "pending";
                // ponytail: no backoff; fixed-interval poll is the retry. Add
                // exponential backoff if a slow mailer causes stampedes.
                queue.markAttempted(job.id(), attempts, failed.getMessage(), status);
            }
        }
        return jobs.size();
    }
}
