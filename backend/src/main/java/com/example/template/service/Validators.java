package com.example.template.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Input validation. Patterns use UNICODE_CHARACTER_CLASS so {@code \d} and
 * {@code \s} are Unicode-aware (Java would otherwise treat them as ASCII-only).
 */
public final class Validators {

    private static final Pattern EMAIL = pattern(
        "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
    );

    // US phone numbers only, digits with optional standard formatting
    // (spaces/dots/dashes/parens) and an optional leading +1/1.
    private static final Pattern PHONE = pattern(
        "^\\+?1?[-.\\s]?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}$"
    );

    // US zip codes only — 5 digits, matching the frontend's pattern="[0-9]{5}".
    private static final Pattern ZIP = pattern("^\\d{5}$");

    private static final Pattern PASSWORD_SPECIAL = pattern(
        "[!@#$%^&*(),.?\":{}|<>]"
    );

    private static final List<String> US_STATE_CODES = List.of(
        "AL",
        "AK",
        "AZ",
        "AR",
        "CA",
        "CO",
        "CT",
        "DE",
        "DC",
        "FL",
        "GA",
        "HI",
        "ID",
        "IL",
        "IN",
        "IA",
        "KS",
        "KY",
        "LA",
        "ME",
        "MD",
        "MA",
        "MI",
        "MN",
        "MS",
        "MO",
        "MT",
        "NE",
        "NV",
        "NH",
        "NJ",
        "NM",
        "NY",
        "NC",
        "ND",
        "OH",
        "OK",
        "OR",
        "PA",
        "RI",
        "SC",
        "SD",
        "TN",
        "TX",
        "UT",
        "VT",
        "VA",
        "WA",
        "WV",
        "WI",
        "WY"
    );

    // One entry today, but a list, so adding a second country later is additive.
    private static final List<String> COUNTRY_CODES = List.of("US");

    private Validators() {}

    private static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS);
    }

    public static boolean isEmail(String email) {
        return EMAIL.matcher(email).matches();
    }

    public static boolean isPhone(String phone) {
        return PHONE.matcher(phone).matches();
    }

    public static boolean isZip(String zip) {
        return ZIP.matcher(zip).matches();
    }

    /** http(s) only — good enough for LinkedIn/GitHub profile links. */
    public static boolean isUrl(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String scheme = uri.getScheme();
            boolean httpScheme =
                scheme != null &&
                ("http".equalsIgnoreCase(scheme) ||
                    "https".equalsIgnoreCase(scheme));
            return httpScheme && uri.getHost() != null;
        } catch (URISyntaxException malformed) {
            return false;
        }
    }

    public static boolean isUsState(String state) {
        return US_STATE_CODES.contains(state);
    }

    public static boolean isCountry(String country) {
        return COUNTRY_CODES.contains(country);
    }

    /**
     * @return a message describing the first unmet rule, or null when the
     *         password is fine.
     */
    public static String validatePassword(String password) {
        // Byte length, not character count: a multi-byte character is worth
        // more than one.
        if (password.getBytes(StandardCharsets.UTF_8).length < 8) {
            return "Password must be at least 8 characters long";
        }
        // ASCII only, deliberately: a non-ASCII uppercase letter or digit does
        // not satisfy these rules.
        if (password.chars().noneMatch(c -> c >= 'A' && c <= 'Z')) {
            return "Password must contain at least one uppercase letter";
        }
        if (password.chars().noneMatch(c -> c >= '0' && c <= '9')) {
            return "Password must contain at least one number";
        }
        if (!PASSWORD_SPECIAL.matcher(password).find()) {
            return "Password must contain at least one special character";
        }
        return null;
    }
}
