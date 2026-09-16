package com.example.template.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * The backend's one database variable is DATABASE_URL, in the libpq form this
 * project has always used ({@code postgres://user:pass@host:port/db?params}). The JDBC
 * driver wants a {@code jdbc:postgresql://} URL with credentials supplied
 * separately, so it is translated here. A URL that is already JDBC-shaped is
 * passed through untouched.
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {
    public static DatabaseUrl parse(String raw) {
        String value = raw.trim();
        if (value.startsWith("jdbc:")) {
            return new DatabaseUrl(value, null, null);
        }
        if (
            !value.startsWith("postgres://") &&
            !value.startsWith("postgresql://")
        ) {
            throw new IllegalStateException(
                "DATABASE_URL must be a postgres:// or jdbc:postgresql:// URL, got: " +
                    raw
            );
        }
        URI uri = URI.create(value);
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
            .append(uri.getHost())
            .append(':')
            .append(port)
            .append(uri.getPath());
        if (uri.getQuery() != null) {
            // libpq parameters such as sslmode are understood by the JDBC
            // driver too, so the query string is carried over verbatim.
            jdbc.append('?').append(uri.getQuery());
        }

        String username = null;
        String password = null;
        if (uri.getUserInfo() != null) {
            String[] credentials = uri.getUserInfo().split(":", 2);
            username = decode(credentials[0]);
            password = credentials.length > 1 ? decode(credentials[1]) : null;
        }
        return new DatabaseUrl(jdbc.toString(), username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
