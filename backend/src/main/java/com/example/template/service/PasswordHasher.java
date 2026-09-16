package com.example.template.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Service;

/**
 * scrypt password hashing, in the exact format the hashes already in the
 * database use, so existing rows keep verifying: {@code hex(salt):hex(key)},
 * N=16384, r=8, p=1, 16-byte salt, 64-byte key. The salt input is the
 * hex-encoded string itself, not the raw bytes.
 */
@Service
public class PasswordHasher {

    private static final int LOG_N = 14; // N = 16384
    private static final int N = 1 << LOG_N;
    private static final int R = 8;
    private static final int P = 1;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 64;

    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        String saltHex = hex(randomBytes(SALT_LENGTH));
        return saltHex + ':' + hex(derive(password, saltHex));
    }

    public boolean verify(String password, String stored) {
        int separator = stored.indexOf(':');
        if (separator < 0) {
            return false;
        }
        String saltHex = stored.substring(0, separator);
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(stored.substring(separator + 1));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        // Constant-time, and a length mismatch simply compares unequal.
        return MessageDigest.isEqual(expected, derive(password, saltHex));
    }

    private static byte[] derive(String password, String saltHex) {
        return SCrypt.generate(
            password.getBytes(StandardCharsets.UTF_8),
            saltHex.getBytes(StandardCharsets.UTF_8),
            N,
            R,
            P,
            KEY_LENGTH
        );
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
