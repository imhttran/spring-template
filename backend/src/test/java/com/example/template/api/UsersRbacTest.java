package com.example.template.api;

import static com.example.template.support.TestEnv.assertStatus;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Client rejected, staff allowed (non-admin rows only), admin allowed.
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
class UsersRbacTest {

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
    void users_rbac() {
        env.signup();
        String token = env.loginAs(env.email(), env.password());
        env.fillProfile(token); // lift the profile gate so RBAC is what's under test

        // Client is rejected.
        assertStatus(403, env.doJson("GET", "/api/users", token, null));

        // Staff is allowed.
        env.setRole(env.email(), "staff");
        TestEnv.Response staff = env.doJson("GET", "/api/users", token, null);
        assertStatus(200, staff);
        assertTrue(
            staff.body().path("users").isArray(),
            () -> "staff: missing users key: " + staff.text()
        );

        // Admin is allowed.
        env.setRole(env.email(), "admin");
        assertStatus(200, env.doJson("GET", "/api/users", token, null));
    }
}
