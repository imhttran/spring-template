package com.example.template.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * One uniquely-addressed fixture per test, driven through the real app with
 * MockMvc, plus the shared helpers — login (including the 2FA round trip), the
 * emailed login code, profile setup, direct role updates and fixture cleanup.
 *
 * <p>Every fixture email is unique per run, so reruns against a dirty database
 * still pass.
 */
public final class TestEnv {

    /** Runs this JVM has started; keeps fixture emails apart. */
    private static final AtomicLong RUN = new AtomicLong();

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Pulls the first 4-digit run out of a queued email body. */
    private static final Pattern LOGIN_CODE = Pattern.compile("\\b\\d{4}\\b");

    /** Produced by {@code SessionRenewalFilter} for sessions past their half-life. */
    public static final String RENEWED_TOKEN_HEADER = "X-Renewed-Token";

    public static final String PASSWORD = "Valid123!";

    /**
     * app.env is deliberately not {@code development}: login codes stay random
     * (login_as pulls them out of the queued email) and the dev-admin seeder
     * stays off. Only the seed test asks for development.
     */
    public static final String APP_ENV_TEST = "app.env=test";

    public static final String APP_ENV_DEVELOPMENT = "app.env=development";

    /** These contexts do not require email verification. */
    public static final String EMAIL_VERIFICATION_NOT_REQUIRED =
        "app.email-verification-required=false";

    public static final String FRONTEND_URL =
        "app.frontend-url=http://localhost:3000";

    /**
     * At least 32 bytes: JwtService refuses a shorter HMAC key, so a shorter
     * secret would fail the context at startup.
     */
    public static final String JWT_SECRET =
        "app.jwt-secret=test-secret-for-integration-tests-32b";

    /** Lets this suite swap the scheduled queue drain for a silent one. */
    public static final String ALLOW_BEAN_OVERRIDE =
        "spring.main.allow-bean-definition-overriding=true";

    /** The database the test context runs against, or null when unset. */
    public static String databaseUrl() {
        return System.getenv("TEST_DATABASE_URL");
    }

    /** A response: the status, the session-renewal header, and the parsed body. */
    public record Response(int status, String renewedToken, JsonNode body) {
        /** The body as text, for assertion messages. */
        public String text() {
            return body.toString();
        }
    }

    private final MockMvc mvc;
    private final JdbcClient jdbc;
    private final String email;

    public TestEnv(MockMvc mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.email =
            "javatest-" +
            RUN.incrementAndGet() +
            "-" +
            (System.nanoTime() % 100_000) +
            "@mail.com";
    }

    public String email() {
        return email;
    }

    public String password() {
        return PASSWORD;
    }

    /** A JSON object that tolerates null values, unlike {@code Map.of}. */
    public static Map<String, Object> json(Object... keysAndValues) {
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

    /** The registration form the profile tests post. */
    public static Map<String, Object> profileBody() {
        return json(
            "firstName",
            "Test",
            "lastName",
            "User",
            "address",
            "1 Test St",
            "state",
            "CA",
            "zip",
            "94043",
            "phone",
            "555-123-4567",
            "communicationPreference",
            "email"
        );
    }

    /**
     * The request carries {@code content-type: application/json} and a bearer
     * token when one is given, and an unparsable body reads as JSON null.
     */
    public Response doJson(
        String method,
        String path,
        String token,
        Map<String, Object> body
    ) {
        MockHttpServletRequestBuilder request = switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(
                "unsupported method " + method
            );
        };
        request.contentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isEmpty()) {
            request.header("Authorization", "Bearer " + token);
        }
        request.content(body == null ? new byte[0] : raw(body));
        try {
            MvcResult result = mvc.perform(request).andReturn();
            MockHttpServletResponse response = result.getResponse();
            return new Response(
                response.getStatus(),
                response.getHeader(RENEWED_TOKEN_HEADER),
                parse(response.getContentAsByteArray())
            );
        } catch (Exception failed) {
            throw new IllegalStateException(
                "request failed: " + method + " " + path,
                failed
            );
        }
    }

    /** A bare signup call — asserting the outcome is the test's job. */
    public Response signup() {
        return doJson(
            "POST",
            "/api/signup",
            "",
            json("email", email, "password", PASSWORD)
        );
    }

    /** Logs in, answering a 2FA challenge with the code out of the queued email. */
    public String loginAs(String email, String password) {
        Response login = doJson(
            "POST",
            "/api/login",
            "",
            json("email", email, "password", password)
        );
        assertStatus(200, login);
        String token = login.body().path("token").asText("");
        assertFalse(
            token.isEmpty(),
            () -> "login for " + email + " returned no token: " + login.text()
        );
        if (login.body().path("twoFactorRequired").asBoolean(false)) {
            String code = fetchLoginCode(email);
            Response verified = doJson(
                "POST",
                "/api/login/verify",
                "",
                json("token", token, "code", code, "deviceId", "test-device")
            );
            assertStatus(200, verified);
            token = verified.body().path("token").asText("");
            assertFalse(
                token.isEmpty(),
                () ->
                    "2FA verify for " +
                    email +
                    " returned no token: " +
                    verified.text()
            );
        }
        return token;
    }

    /**
     * The newest queued email for the address, with the 4-digit login code
     * pulled out of its body (the worker isn't what delivers it in tests).
     */
    public String fetchLoginCode(String email) {
        String body = jdbc
            .sql(
                "SELECT body FROM email_queue WHERE \"to\" = :email ORDER BY id DESC LIMIT 1"
            )
            .param("email", email)
            .query(String.class)
            .optional()
            .orElseThrow(() ->
                new IllegalStateException("no queued email for " + email)
            );
        Matcher match = LOGIN_CODE.matcher(body);
        if (!match.find()) {
            throw new IllegalStateException(
                "no 4-digit code in queued email: " + body
            );
        }
        return match.group();
    }

    /** Fills in the profile so onboarding gates don't mask the behaviour under test. */
    public void fillProfile(String token) {
        Response saved = doJson("POST", "/api/profile", token, profileBody());
        assertStatus(201, saved);
    }

    /** Sets a user's role directly in the store. */
    public void setRole(String email, String role) {
        jdbc.sql("UPDATE users SET role = :role WHERE email = :email")
            .param("role", role)
            .param("email", email)
            .update();
    }

    public String roleOf(String email) {
        return jdbc
            .sql("SELECT role FROM users WHERE email = :email")
            .param("email", email)
            .query(String.class)
            .optional()
            .orElse(null);
    }

    public int ownUserId(String token) {
        Response me = doJson("GET", "/api/me", token, null);
        assertStatus(200, me);
        JsonNode id = me.body().path("user").path("id");
        assertTrue(
            id.isNumber(),
            () -> "/api/me returned no user id: " + me.text()
        );
        return id.asInt();
    }

    /**
     * Deletes the fixture's rows: the user (which cascades to the profile) and
     * the queued emails addressed to it.
     */
    public void cleanup() {
        jdbc.sql("DELETE FROM users WHERE email = :email")
            .param("email", email)
            .update();
        jdbc.sql("DELETE FROM email_queue WHERE \"to\" = :email")
            .param("email", email)
            .update();
    }

    /** Status assertion, with the body included in the failure message. */
    public static void assertStatus(int want, Response response) {
        assertEquals(want, response.status(), () -> "body " + response.text());
    }

    /** {@code assert_eq!(body[key], json!(true))} — boolean true, not just truthy. */
    public static void assertJsonTrue(String key, Response response) {
        JsonNode value = response.body().path(key);
        assertTrue(
            value.isBoolean() && value.booleanValue(),
            () -> key + " = " + response.text()
        );
    }

    /** {@code assert_ne!(body[key], json!(true))}. */
    public static void assertJsonNotTrue(String key, Response response) {
        JsonNode value = response.body().path(key);
        assertFalse(
            value.isBoolean() && value.booleanValue(),
            () -> key + " = " + response.text()
        );
    }

    /** {@code assert_eq!(body["message"], json!(…))}, with the body in the message. */
    public static void assertMessage(String want, Response response) {
        assertEquals(
            want,
            response.body().path("message").asText(),
            () -> "body " + response.text()
        );
    }

    private static byte[] raw(Map<String, Object> body) {
        try {
            return JSON.writeValueAsBytes(body);
        } catch (JsonProcessingException unexpected) {
            throw new IllegalArgumentException(
                "body is not serialisable: " + body,
                unexpected
            );
        }
    }

    /** Missing or unparsable bodies read as JSON null. */
    private static JsonNode parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return NullNode.getInstance();
        }
        try {
            return JSON.readTree(bytes);
        } catch (IOException unparsable) {
            return NullNode.getInstance();
        }
    }
}
