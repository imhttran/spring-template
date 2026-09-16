package com.example.template.api;

import static com.example.template.support.TestEnv.assertStatus;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.template.service.JwtService;
import com.example.template.support.NoScheduledEmailWorker;
import com.example.template.support.TestEnv;
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
 * A token deep
 * into its life is renewed on successful use (X-Renewed-Token, produced by
 * {@link SessionRenewalFilter}), the renewed token is a normal bearer, a
 * full-life token is not renewed, and a hard-expired one is rejected.
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
class SessionSlidesWhileActiveTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JwtService jwt;

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
    void session_slides_while_active() {
        env.signup();

        // 60 seconds left of the 10-minute window → renewed on use.
        String aging = jwt.issueWithTtl(env.email(), 60);
        TestEnv.Response sliding = env.doJson("GET", "/api/me", aging, null);
        assertStatus(200, sliding);
        String renewed = sliding.renewedToken();
        assertNotNull(renewed, "aging token should be renewed");
        assertNotEquals(aging, renewed, "renewal must be a new token");

        // The renewed token is a normal bearer.
        assertStatus(200, env.doJson("GET", "/api/me", renewed, null));

        // A fresh (full-life) token is not renewed.
        String fresh = jwt.issue(env.email());
        TestEnv.Response full = env.doJson("GET", "/api/me", fresh, null);
        assertStatus(200, full);
        assertNull(full.renewedToken(), "full-life token should not renew");

        // Hard expiry: past the window, the token is rejected outright.
        String expired = jwt.issueWithTtl(env.email(), -60);
        assertStatus(403, env.doJson("GET", "/api/me", expired, null));
    }
}
