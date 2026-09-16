package com.example.template.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.template.repository.Profile;
import com.example.template.repository.ProfileRepository;
import com.example.template.repository.UserRepository;
import com.example.template.service.PasswordHasher;

/**
 * Dev-only convenience: guarantees a known admin login exists locally, so
 * there's no manual set-role step for local dev. Gated on NODE_ENV so these
 * credentials can never appear in a qa/prod database.
 */
@Component
public class DevAdminSeeder implements ApplicationRunner {

    static final String DEV_ADMIN_EMAIL = "admin@mail.com";
    static final String DEV_ADMIN_PASSWORD = "Password1234!";

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);

    private final AppProperties properties;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final PasswordHasher hasher;

    public DevAdminSeeder(AppProperties properties, UserRepository users, ProfileRepository profiles,
            PasswordHasher hasher) {
        this.properties = properties;
        this.users = users;
        this.profiles = profiles;
        this.hasher = hasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isDevelopment()) {
            return;
        }
        try {
            // Conflict (no row) and DB errors both fall through to the lookup.
            Integer id = users
                    .insertUserIfAbsent(DEV_ADMIN_EMAIL, hasher.hash(DEV_ADMIN_PASSWORD), "admin", true)
                    .orElse(null);
            if (id == null) {
                id = users.findIdByEmail(DEV_ADMIN_EMAIL).orElse(null);
            }
            if (id == null) {
                log.error("[seed] failed: dev admin missing after insert");
                return;
            }
            // Pre-fill the profile too, so the dev admin isn't stopped by its
            // own onboarding gate.
            profiles.insertIfAbsent(new Profile(0, id, "Dev", "Admin", "N/A", null, "N/A", "00000", "US",
                    "N/A", "email", null, null, null));
            log.info("[seed] dev admin ready: {}", DEV_ADMIN_EMAIL);
        } catch (Exception failed) {
            log.error("[seed] failed: {}", failed.getMessage());
        }
    }
}
