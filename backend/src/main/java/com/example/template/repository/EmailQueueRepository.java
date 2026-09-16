package com.example.template.repository;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The Postgres-backed outbound mail queue. Rows are written inside the same
 * transaction as the change that caused them, and drained by the worker
 * ({@code EmailWorker}) — so no request ever blocks on a mail server.
 */
@Repository
public class EmailQueueRepository {

    private final JdbcClient jdbc;

    public EmailQueueRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Job(int id, String to, String subject, String body, int attempts) {
    }

    public void enqueue(String to, String subject, String body) {
        jdbc.sql("INSERT INTO email_queue (\"to\", subject, body) VALUES (:to, :subject, :body)")
                .param("to", to)
                .param("subject", subject)
                .param("body", body)
                .update();
    }

    public List<Job> findPending(int maxAttempts, int limit) {
        return jdbc.sql("""
                SELECT id, "to", subject, body, attempts
                FROM email_queue
                WHERE status = 'pending' AND attempts < :maxAttempts
                ORDER BY created_at ASC
                LIMIT :limit
                """)
                .param("maxAttempts", maxAttempts)
                .param("limit", limit)
                .query(Job.class)
                .list();
    }

    public void markSent(int id) {
        jdbc.sql("UPDATE email_queue SET status = 'sent', sent_at = now() WHERE id = :id")
                .param("id", id)
                .update();
    }

    public void markAttempted(int id, int attempts, String error, String status) {
        jdbc.sql("""
                UPDATE email_queue SET attempts = :attempts, last_error = :error, status = :status
                WHERE id = :id
                """)
                .param("attempts", attempts)
                .param("error", error)
                .param("status", status)
                .param("id", id)
                .update();
    }
}
