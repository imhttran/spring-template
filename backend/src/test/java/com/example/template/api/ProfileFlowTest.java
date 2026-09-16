package com.example.template.api;

import static com.example.template.support.TestEnv.assertMessage;
import static com.example.template.support.TestEnv.assertStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.template.support.NoScheduledEmailWorker;
import com.example.template.support.TestEnv;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The gate lift, validation and the unique-violation path, end to end.
 */
@SpringBootTest(
    properties = {
        TestEnv.APP_ENV_TEST,
        TestEnv.EMAIL_VERIFICATION_NOT_REQUIRED,
        TestEnv.FRONTEND_URL,
        TestEnv.JWT_SECRET,
        TestEnv.ALLOW_BEAN_OVERRIDE,
    }
)
@AutoConfigureMockMvc
@Import(NoScheduledEmailWorker.class) // these tests drive the queue themselves
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // close the context (and its 10-connection pool) after each class
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ProfileFlowTest {

    /** One rejected form and the message the route must answer it with. */
    private record InvalidCase(Map<String, Object> payload, String want) {}

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private TestEnv env;

    @DynamicPropertySource
    static void databaseUrl(DynamicPropertyRegistry registry) {
        registry.add("app.database-url", TestEnv::databaseUrl);
    }

    @BeforeEach
    void setUp() {
        env = new TestEnv(mvc, jdbc);
    }

    @AfterEach
    void cleanup() {
        env.cleanup();
    }

    @Test
    void profile_flow() {
        env.signup();
        String token = env.loginAs(env.email(), env.password());

        // Before saving, the profile is a 200 with null (the absence is the gate).
        TestEnv.Response empty = env.doJson("GET", "/api/profile", token, null);
        assertStatus(200, empty);
        assertTrue(
            empty.body().path("profile").isNull(),
            () -> "body " + empty.text()
        );

        env.fillProfile(token);

        // Second save hits the unique constraint → "Profile already exists".
        TestEnv.Response duplicate = env.doJson(
            "POST",
            "/api/profile",
            token,
            TestEnv.profileBody()
        );
        assertStatus(400, duplicate);
        assertMessage("Profile already exists", duplicate);

        // The saved row comes back with camelCase fields and the US default.
        TestEnv.Response saved = env.doJson("GET", "/api/profile", token, null);
        assertStatus(200, saved);
        assertEquals(
            "Test",
            saved.body().path("profile").path("firstName").asText(),
            () -> "body " + saved.text()
        );
        assertEquals(
            "email",
            saved
                .body()
                .path("profile")
                .path("communicationPreference")
                .asText(),
            () -> "body " + saved.text()
        );
        assertEquals(
            "US",
            saved.body().path("profile").path("country").asText(),
            () -> "body " + saved.text()
        );
        assertTrue(
            saved.body().path("profile").path("address2").isNull(),
            () -> "body " + saved.text()
        );

        // Validation runs before the insert, so these 400s don't mention profiles.
        List<InvalidCase> cases = List.of(
            new InvalidCase(
                TestEnv.json(
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
                    "not-a-phone",
                    "communicationPreference",
                    "email"
                ),
                "Phone number is invalid"
            ),
            new InvalidCase(
                TestEnv.json(
                    "firstName",
                    "Test",
                    "lastName",
                    "User",
                    "address",
                    "1 Test St",
                    "state",
                    "XX",
                    "zip",
                    "94043",
                    "phone",
                    "555-123-4567",
                    "communicationPreference",
                    "email"
                ),
                "State is invalid"
            ),
            new InvalidCase(
                TestEnv.json(
                    "firstName",
                    "Test",
                    "lastName",
                    "User",
                    "address",
                    "1 Test St",
                    "state",
                    "CA",
                    "phone",
                    "555-123-4567",
                    "communicationPreference",
                    "email"
                ),
                "Missing required field(s): zip"
            )
        );
        for (InvalidCase rejected : cases) {
            TestEnv.Response response = env.doJson(
                "POST",
                "/api/profile",
                token,
                rejected.payload()
            );
            assertStatus(400, response);
            assertMessage(rejected.want(), response);
        }
    }
}
