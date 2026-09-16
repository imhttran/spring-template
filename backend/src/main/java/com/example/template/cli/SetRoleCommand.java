package com.example.template.cli;

import com.example.template.config.DatabaseUrl;
import com.example.template.service.Roles;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

/**
 * {@code set-role <email> <role>}: roles are granted out-of-band, so there is
 * no HTTP endpoint for this. {@link #run} deliberately does not load .env files
 * and does not start Spring — it reads DATABASE_URL directly, like the CLI it
 * replaces. {@link #apply} is the role change itself, split out so it can be
 * driven against any connection.
 */
public final class SetRoleCommand {

    /** What happened, independent of how the change was reached. */
    public enum Outcome {
        OK,
        NO_SUCH_USER,
        INVALID_ROLE,
    }

    private static final String DEFAULT_DATABASE_URL =
        "postgres://postgres:postgres@localhost:5432/template-db?sslmode=disable";

    private SetRoleCommand() {}

    /** @return the process exit code. */
    public static int run(String[] args) {
        String usage =
            "Usage: set-role <email> <" + String.join("|", Roles.ROLES) + ">";
        if (args.length != 2 || !Roles.isRole(args[1])) {
            System.err.println(usage);
            return 1;
        }
        String email = args[0];
        String role = args[1];

        String dsn = System.getenv("DATABASE_URL");
        if (dsn == null || dsn.isEmpty()) {
            dsn = DEFAULT_DATABASE_URL;
        }
        DatabaseUrl url;
        try {
            url = DatabaseUrl.parse(dsn);
        } catch (IllegalStateException malformed) {
            System.err.println("Failed to set role: " + malformed.getMessage());
            return 1;
        }

        try (Connection connection = open(url)) {
            Outcome outcome = apply(connection, email, role);
            if (outcome == Outcome.OK) {
                System.out.println(email + " is now " + role);
                return 0;
            }
            if (outcome == Outcome.NO_SUCH_USER) {
                System.err.println("No user found with email " + email);
                return 1;
            }
            System.err.println(usage);
            return 1;
        } catch (SQLException failed) {
            System.err.println("Failed to set role: " + failed.getMessage());
            return 1;
        }
    }

    /**
     * Sets an existing user's role — the same code path the CLI runs, against
     * whatever connection the caller supplies.
     */
    public static Outcome apply(
        Connection connection,
        String email,
        String role
    ) throws SQLException {
        if (!Roles.isRole(role)) {
            return Outcome.INVALID_ROLE;
        }
        if (!userExists(connection, email)) {
            return Outcome.NO_SUCH_USER;
        }
        try (
            PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET role = ? WHERE email = ?"
            )
        ) {
            statement.setString(1, role);
            statement.setString(2, email);
            statement.executeUpdate();
        }
        return Outcome.OK;
    }

    private static Connection open(DatabaseUrl url) throws SQLException {
        Properties credentials = new Properties();
        if (url.username() != null) {
            credentials.setProperty("user", url.username());
        }
        if (url.password() != null) {
            credentials.setProperty("password", url.password());
        }
        return DriverManager.getConnection(url.jdbcUrl(), credentials);
    }

    private static boolean userExists(Connection connection, String email)
        throws SQLException {
        try (
            PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM users WHERE email = ?)"
            )
        ) {
            statement.setString(1, email);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        }
    }
}
