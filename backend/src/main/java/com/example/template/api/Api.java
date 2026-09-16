package com.example.template.api;

import com.example.template.service.AuthUser;
import com.example.template.service.Roles;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Response shapes and request decoding — the {@code msg}/{@code fail} helpers
 * and the lenient body decoder, both pinned by the API contract.
 *
 * <p>Two body shapes are in the contract and they are not interchangeable: a
 * bare {@code {"message": …}} (used by the auth gates, the profile form and the
 * user-management routes) and {@code {"success": false, "message": …}} (used by
 * signup, login, password reset and admin user creation). {@link #msg} and
 * {@link #fail} build each.
 */
public final class Api {

    /**
     * Missing and unparsable bodies decode as an empty request object, exactly
     * like {@code serde_json::from_slice(...).unwrap_or_default()} — route-level
     * validation is what produces the 400s. Unknown fields are ignored, which
     * is serde's default too.
     */
    private static final ObjectMapper DECODER = new ObjectMapper().configure(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        false
    );

    private Api() {}

    /** A JSON object that tolerates null values, unlike {@code Map.of}. */
    public static Map<String, Object> body(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException(
                "keys and values must come in pairs"
            );
        }
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            body.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return body;
    }

    public static Map<String, Object> msg(String message) {
        return body("message", message);
    }

    public static Map<String, Object> fail(String message) {
        return body("success", false, "message", message);
    }

    public static ResponseEntity<Object> respond(
        HttpStatus status,
        Map<String, Object> body
    ) {
        return ResponseEntity.status(status).body(body);
    }

    /** Decodes a request body, falling back to an empty request object. */
    public static <T> T decode(byte[] raw, Class<T> type) {
        if (raw == null || raw.length == 0) {
            return empty(type);
        }
        try {
            return DECODER.readValue(raw, type);
        } catch (IOException | RuntimeException unparsable) {
            return empty(type);
        }
    }

    /** A loosely decoded body for handlers that only need to sniff one field. */
    public static JsonNode decodeNode(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return MissingNode.getInstance();
        }
        try {
            return DECODER.readTree(raw);
        } catch (IOException unparsable) {
            return MissingNode.getInstance();
        }
    }

    /**
     * Role gate for the staff/admin routes: 403s unless the user's role is
     * {@code minimumRole} or higher.
     */
    public static void requireRole(AuthUser user, String minimumRole) {
        if (!Roles.hasRole(user.role(), minimumRole)) {
            throw new ApiRejection(
                HttpStatus.FORBIDDEN,
                msg("Insufficient permissions")
            );
        }
    }

    /** Path ids arrive as text, so an unparsable one is our 400, not Spring's. */
    public static int parseId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException malformed) {
            throw new ApiRejection(
                HttpStatus.BAD_REQUEST,
                msg("Invalid user id")
            );
        }
    }

    private static <T> T empty(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException noDefaultConstructor) {
            throw new IllegalArgumentException(
                type.getName() + " needs a no-arg constructor",
                noDefaultConstructor
            );
        }
    }
}
