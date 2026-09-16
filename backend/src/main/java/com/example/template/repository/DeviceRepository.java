package com.example.template.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Trusted devices skip the 2FA code on future logins. */
@Repository
public class DeviceRepository {

    private final JdbcClient jdbc;

    public DeviceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isTrusted(int userId, String deviceId) {
        return Boolean.TRUE.equals(jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM user_devices WHERE user_id = :userId AND device_id = :deviceId)
                """)
                .param("userId", userId)
                .param("deviceId", deviceId)
                .query(Boolean.class)
                .single());
    }

    public void trust(int userId, String deviceId) {
        jdbc.sql("""
                INSERT INTO user_devices (user_id, device_id) VALUES (:userId, :deviceId)
                ON CONFLICT (device_id) DO NOTHING
                """)
                .param("userId", userId)
                .param("deviceId", deviceId)
                .update();
    }
}
