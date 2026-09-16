package com.example.template.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.example.template.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Real SMTP when SMTP_HOST is set; otherwise the email is logged instead of
 * sent, so dev works with no mail server.
 */
@Service
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final AppProperties properties;
    private final JavaMailSender sender;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean noticeLogged = new AtomicBoolean(false);

    public Mailer(AppProperties properties, JavaMailSender sender, ObjectMapper objectMapper) {
        this.properties = properties;
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    /**
     * @throws org.springframework.mail.MailException when the message can't be
     *         built or the mail server rejects it. The queue worker records the
     *         message and retries.
     */
    public void send(String to, String subject, String text) {
        if (properties.getSmtpHost().isEmpty()) {
            if (noticeLogged.compareAndSet(false, true)) {
                log.info("[mailer] SMTP_HOST not set — emails are logged, not sent.");
            }
            log.info("[mailer] email: {}", asJson(to, subject, text));
            return;
        }
        if (hasLineBreak(to) || hasLineBreak(subject) || hasLineBreak(properties.getMailFrom())) {
            throw new MailParseException("invalid header characters in email");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }

    /** Header injection guard — nothing user-supplied may break out of a header. */
    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private String asJson(String to, String subject, String text) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("from", properties.getMailFrom());
        fields.put("to", to);
        fields.put("subject", subject);
        fields.put("text", text);
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException unexpected) {
            return fields.toString();
        }
    }
}
