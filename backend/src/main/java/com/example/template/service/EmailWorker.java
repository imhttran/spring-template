package com.example.template.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the email queue and sends (or logs) whatever is pending. */
@Component
public class EmailWorker {

    private static final int BATCH_SIZE = 10;

    private final EmailQueueService emailQueue;

    public EmailWorker(EmailQueueService emailQueue) {
        this.emailQueue = emailQueue;
    }

    @Scheduled(fixedDelay = 3000)
    public void drainQueue() {
        emailQueue.process(BATCH_SIZE);
    }
}
