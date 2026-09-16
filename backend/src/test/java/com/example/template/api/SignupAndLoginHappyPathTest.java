package com.example.template.api;

import static com.example.template.support.TestEnv.assertJsonTrue;
import static com.example.template.support.TestEnv.assertStatus;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

/** Signup then login, end to end. */
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
class SignupAndLoginHappyPathTest {

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
    void signup_and_login_happy_path() {
        TestEnv.Response signup = env.doJson(
            "POST",
            "/api/signup",
            "",
            TestEnv.json("email", env.email(), "password", env.password())
        );
        assertStatus(201, signup);
        assertJsonTrue("success", signup);

        TestEnv.Response login = env.doJson(
            "POST",
            "/api/login",
            "",
            TestEnv.json("email", env.email(), "password", env.password())
        );
        assertStatus(200, login);
        assertFalse(
            login.body().path("token").asText("").isEmpty(),
            () -> "login returned no token: " + login.text()
        );
    }
}
