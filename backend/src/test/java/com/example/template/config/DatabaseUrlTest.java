package com.example.template.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

    @Test
    void translatesTheLibpqFormTheBackendHasAlwaysUsed() {
        DatabaseUrl url = DatabaseUrl
                .parse("postgres://postgres:postgres@localhost:5432/template-db?sslmode=disable");
        assertEquals("jdbc:postgresql://localhost:5432/template-db?sslmode=disable", url.jdbcUrl());
        assertEquals("postgres", url.username());
        assertEquals("postgres", url.password());
    }

    @Test
    void defaultsThePortAndToleratesMissingCredentials() {
        DatabaseUrl url = DatabaseUrl.parse("postgresql://db.example.com/template-db");
        assertEquals("jdbc:postgresql://db.example.com:5432/template-db", url.jdbcUrl());
        assertNull(url.username());
        assertNull(url.password());
    }

    @Test
    void passesAUrlThatIsAlreadyJdbcShaped() {
        DatabaseUrl url = DatabaseUrl.parse("jdbc:postgresql://localhost:5432/template-db");
        assertEquals("jdbc:postgresql://localhost:5432/template-db", url.jdbcUrl());
    }

    @Test
    void decodesEscapedCredentials() {
        DatabaseUrl url = DatabaseUrl.parse("postgres://user%40corp:p%40ss%3Aword@localhost:5432/template-db");
        assertEquals("user@corp", url.username());
        assertEquals("p@ss:word", url.password());
    }

    @Test
    void rejectsAnythingElse() {
        assertThrows(IllegalStateException.class, () -> DatabaseUrl.parse("mysql://localhost/template-db"));
    }
}
