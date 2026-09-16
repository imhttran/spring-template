package com.example.template.support;

import com.example.template.service.EmailQueueService;
import com.example.template.service.EmailWorker;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Spring starts the {@code @Scheduled} drain with the context (and keeps it
 * running while the context stays cached), which would race the queue test and
 * drain rows it is counting.
 *
 * <p>Importing this replaces that bean with a silent one, so tests decide when
 * {@link EmailQueueService#process(int)} runs.
 */
@TestConfiguration
public class NoScheduledEmailWorker {

    @Bean
    EmailWorker emailWorker(EmailQueueService queue) {
        return new EmailWorker(queue) {
            @Override
            public void drainQueue() {
                // Silenced on purpose; see this class's javadoc.
            }
        };
    }
}
