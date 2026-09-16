package com.example.template.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime configuration, bound from environment variables (see
 * application.yml). Normalisation mirrors Go's {@code envOr}
 * / {@code intOr} helpers: an empty value counts as unset, and a
 * non-positive/unparsable number falls back to its default.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private static final Logger log = LoggerFactory.getLogger(
        AppProperties.class
    );

    /**
     * Fallback for local development only. At least 32 bytes, because
     * JwtService refuses a shorter HMAC key. Only affects sessions issued while
     * running on the fallback — set JWT_SECRET for anything shared.
     */
    static final String INSECURE_DEV_JWT_SECRET =
        "dev-insecure-jwt-secret-not-for-production";

    private String env;
    private String databaseUrl;
    private String frontendUrl;
    private String jwtSecret;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUser = "";
    private String smtpPass = "";
    private String mailFrom;
    private Integer maxAttempts;
    private Boolean emailVerificationRequired;

    @PostConstruct
    void normalise() {
        if (isBlank(env)) {
            env = "development";
        }
        if (isBlank(databaseUrl)) {
            databaseUrl =
                "postgres://postgres:postgres@localhost:5432/template-db?sslmode=disable";
        }
        if (isBlank(frontendUrl)) {
            frontendUrl = "http://localhost:3000";
        }
        if (isBlank(mailFrom)) {
            mailFrom = "no-reply@example.com";
        }
        if (smtpPort == null || smtpPort <= 0) {
            smtpPort = 587;
        }
        if (maxAttempts == null || maxAttempts <= 0) {
            maxAttempts = 3;
        }
        if (emailVerificationRequired == null) {
            // Unset or empty counts as required.
            emailVerificationRequired = true;
        }
        if (isBlank(smtpHost)) {
            smtpHost = "";
        }
        if (isBlank(jwtSecret)) {
            if (isProduction()) {
                throw new IllegalStateException(
                    "JWT_SECRET must be set in production"
                );
            }
            log.warn(
                "[config] JWT_SECRET not set — using insecure dev fallback"
            );
            jwtSecret = INSECURE_DEV_JWT_SECRET;
        }
    }

    public boolean isProduction() {
        return "production".equals(env);
    }

    public boolean isDevelopment() {
        return "development".equals(env);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public void setDatabaseUrl(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(Integer smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUser() {
        return smtpUser;
    }

    public void setSmtpUser(String smtpUser) {
        this.smtpUser = smtpUser;
    }

    public String getSmtpPass() {
        return smtpPass;
    }

    public void setSmtpPass(String smtpPass) {
        this.smtpPass = smtpPass;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isEmailVerificationRequired() {
        return emailVerificationRequired;
    }

    public void setEmailVerificationRequired(
        Boolean emailVerificationRequired
    ) {
        this.emailVerificationRequired = emailVerificationRequired;
    }
}
