package com.reportingportal.auth;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Reads users and their roles from the PORTAL schema. Every query uses
 * PreparedStatement with ? placeholders: the username typed on the login form
 * is sent to Oracle as a bound value and is never part of the SQL text.
 */
public class UserDao {

    /** A user row as stored, including the hash; never put into the session. */
    public record StoredUser(long userId, String username, String displayName, String passwordHash) {
    }

    private static final String FIND_ACTIVE_USER =
            "SELECT user_id, username, display_name, password_hash "
          + "FROM users WHERE username = ? AND is_active = 'Y'";

    private static final String FIND_ROLES =
            "SELECT role_code FROM user_roles WHERE user_id = ?";

    private static final String RECORD_LOGIN =
            "UPDATE users SET last_login_at = ? WHERE user_id = ?";

    private final DataSource dataSource;

    public UserDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<StoredUser> findActiveByUsername(String username) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_USER)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredUser(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("password_hash")));
            }
        }
    }

    public Set<String> findRoles(long userId) throws SQLException {
        Set<String> roles = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ROLES)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    roles.add(rs.getString("role_code"));
                }
            }
        }
        return roles;
    }

    /** The time comes from the JVM (running in UTC), like every timestamp the portal writes. */
    public void recordLogin(long userId, Instant when) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(RECORD_LOGIN)) {
            statement.setTimestamp(1, Timestamp.from(when));
            statement.setLong(2, userId);
            statement.executeUpdate();   // the pool's connections auto-commit
        }
    }
}
