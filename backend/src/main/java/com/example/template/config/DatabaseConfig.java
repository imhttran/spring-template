package com.example.template.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class DatabaseConfig {

    /**
     * Built by hand rather than from {@code spring.datasource.*} because the
     * single source of truth is DATABASE_URL (see {@link DatabaseUrl}).
     */
    @Bean
    public DataSource dataSource(AppProperties properties) {
        DatabaseUrl url = DatabaseUrl.parse(properties.getDatabaseUrl());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url.jdbcUrl());
        if (url.username() != null) {
            config.setUsername(url.username());
        }
        if (url.password() != null) {
            config.setPassword(url.password());
        }
        return new HikariDataSource(config);
    }

    /**
     * The repository layer's only database dependency. Every query is raw SQL —
     * no ORM.
     */
    @Bean
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }
}
