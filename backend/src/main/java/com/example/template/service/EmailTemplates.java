package com.example.template.service;

/**
 * The email bodies. The wording and the links are deliberately unchanged, so
 * recipients see exactly what this template has always sent.
 */
public final class EmailTemplates {

    public record Email(String to, String subject, String body) {}

    private EmailTemplates() {}

    public static Email welcome(String to) {
        return new Email(
            to,
            "Your account has been created",
            "Hi,\n\nYou've been successfully added to our system.\n\nThanks,\nThe Team"
        );
    }

    public static Email verification(String to, String link) {
        return new Email(
            to,
            "Verify your email address",
            "Hi,\n\nPlease verify your email address by visiting this link:\n\n" +
                link +
                "\n\nThanks,\nThe Team"
        );
    }

    public static Email passwordReset(String to, String link) {
        return new Email(
            to,
            "Reset your password",
            "Hi,\n\nA password reset was requested for this account. Click the link below to choose a new" +
                " password (expires in 1 hour):\n\n" +
                link +
                "\n\nIf you didn't request this, you can ignore this email.\n\nThanks,\nThe Team"
        );
    }

    public static Email loginCode(String to, String code) {
        return new Email(
            to,
            "Your login code",
            "Hi,\n\nYour login verification code is:\n\n" +
                code +
                "\n\nIt expires in 10 minutes.\n\nThanks,\nThe Team"
        );
    }

    /** Next.js client routes (no .html). */
    public static String tokenLink(
        String frontendUrl,
        String page,
        String token
    ) {
        return frontendUrl + "/" + page + "?token=" + token;
    }
}
