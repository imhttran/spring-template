package com.example.template.config;

import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    /**
     * Real SMTP when SMTP_HOST is set; when it isn't, Mailer logs the email
     * instead of sending, so dev needs no mail server. That decision lives in
     * Mailer, so the sender is always built here.
     */
    @Bean
    public JavaMailSender javaMailSender(AppProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getSmtpHost());
        sender.setPort(properties.getSmtpPort());
        if (!properties.getSmtpUser().isEmpty()) {
            sender.setUsername(properties.getSmtpUser());
            sender.setPassword(properties.getSmtpPass());
        }
        Properties mail = sender.getJavaMailProperties();
        if (properties.getSmtpPort() == 465) {
            // Implicit TLS.
            mail.put("mail.smtp.ssl.enable", "true");
        } else {
            // Opportunistic STARTTLS: use it when the server offers it, carry on
            // in the clear when it doesn't.
            mail.put("mail.smtp.starttls.enable", "true");
            mail.put("mail.smtp.starttls.required", "false");
        }
        // Bounded waits so an unreachable mail server can't wedge the queue
        // worker (the worker is single threaded).
        mail.put("mail.smtp.connectiontimeout", "10000");
        mail.put("mail.smtp.timeout", "10000");
        mail.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
