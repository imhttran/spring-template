package com.example.template.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * The .env loader, so the same files (and the same precedence) keep configuring
 * the app:
 *
 * <ul>
 *   <li>a personal root {@code .env} always wins — real environment variables
 *       are never overwritten;</li>
 *   <li>{@code .env.dev} only fills in when NODE_ENV is unset or
 *       {@code development} (otherwise it could never be seen, since it is
 *       itself what sets NODE_ENV);</li>
 *   <li>files are resolved from the working directory, then the parent.</li>
 * </ul>
 *
 * Registered in {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}.
 * Values are added as property sources just below the real environment, so
 * precedence is: environment → .env → .env.dev → application.yml defaults.
 */
public class EnvFiles implements EnvironmentPostProcessor {

    private static final String ROOT_ENV = ".env";
    private static final String DEV_ENV = ".env.dev";

    /** Working directory first, then its parent — the first file that exists wins. */
    private static final String[] DIRS = { ".", ".." };

    @Override
    public void postProcessEnvironment(
        ConfigurableEnvironment environment,
        SpringApplication application
    ) {
        Map<String, String> rootEnv = readFirstExisting(ROOT_ENV);
        addBelowEnvironment(environment, rootEnv, ROOT_ENV);

        String nodeEnv = System.getenv("NODE_ENV");
        if (isBlank(nodeEnv)) {
            nodeEnv = rootEnv.get("NODE_ENV");
        }
        if (!isBlank(nodeEnv) && !"development".equals(nodeEnv)) {
            return;
        }

        Map<String, String> devEnv = readFirstExisting(DEV_ENV);
        // Real environment variables and .env both beat .env.dev.
        devEnv
            .keySet()
            .removeIf(
                key -> System.getenv(key) != null || rootEnv.containsKey(key)
            );
        addBelowEnvironment(environment, devEnv, DEV_ENV);
    }

    private static void addBelowEnvironment(
        ConfigurableEnvironment environment,
        Map<String, String> values,
        String fileName
    ) {
        if (values.isEmpty()) {
            return;
        }
        MapPropertySource source = new MapPropertySource(
            "envFile [" + fileName + "]",
            new LinkedHashMap<String, Object>(values)
        );
        if (
            environment
                .getPropertySources()
                .contains(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME
                )
        ) {
            environment
                .getPropertySources()
                .addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    source
                );
        } else {
            environment.getPropertySources().addLast(source);
        }
    }

    private static Map<String, String> readFirstExisting(String fileName) {
        for (String dir : DIRS) {
            Path path = Path.of(dir).resolve(fileName);
            if (Files.exists(path)) {
                return parse(path);
            }
        }
        return new LinkedHashMap<>();
    }

    /** {@code KEY=value}, blank lines and #comments skipped, optional `export`, quotes stripped. */
    private static Map<String, String> parse(Path path) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(
                path,
                StandardCharsets.UTF_8
            )) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length());
                }
                int separator = line.indexOf('=');
                if (separator < 0) {
                    continue;
                }
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if (
                    value.length() >= 2 &&
                    ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'")))
                ) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
        } catch (IOException ignored) {
            // An unreadable file is treated as absent.
        }
        return values;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }
}
