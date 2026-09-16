package com.example.template.service;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.example.template.config.AppProperties;

/** The random values the auth flow hands out. */
@Service
public class Tokens {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final AppProperties properties;

    public Tokens(AppProperties properties) {
        this.properties = properties;
    }

    /** 32 random bytes, hex encoded — verification, reset and pending-login tokens. */
    public String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * A 4-digit login code. In development it is always 1234 so testing needs
     * no mail server; otherwise a random code.
     */
    public String randomCode() {
        if (properties.isDevelopment()) {
            return "1234";
        }
        byte[] bytes = new byte[2];
        random.nextBytes(bytes);
        int value = (((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF)) % 10000;
        return String.format("%04d", value);
    }
}
