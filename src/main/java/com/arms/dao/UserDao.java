package com.arms.dao;

import com.arms.model.Role;
import com.arms.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class UserDao {

    /** What login needs, including the hash and lockout state. Never leaves the service layer. */
    public record Credentials(User user, String passwordHash, int failedAttempts, LocalDateTime lockedUntil) {
    }

    public long insert(Connection c, String username, String fullName, String passwordHash, Role role) throws SQLException {
        String sql = "INSERT INTO users (username, full_name, password_hash, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, fullName);
            ps.setString(3, passwordHash);
            ps.setString(4, role.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public boolean exists(Connection c, String username) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Locks the user row (FOR UPDATE) so two simultaneous logins can't lose a failed-attempt count. */
    public Credentials findForLogin(Connection c, String username) throws SQLException {
        String sql = "SELECT user_id, username, full_name, role, password_hash, failed_attempts, locked_until "
                + "FROM users WHERE username = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                User user = new User(rs.getLong("user_id"), rs.getString("username"),
                        rs.getString("full_name"), Role.valueOf(rs.getString("role")));
                Timestamp locked = rs.getTimestamp("locked_until");
                return new Credentials(user, rs.getString("password_hash"), rs.getInt("failed_attempts"),
                        locked == null ? null : locked.toLocalDateTime());
            }
        }
    }

    public void updateLoginState(Connection c, long userId, int failedAttempts, LocalDateTime lockedUntil) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE user_id = ?")) {
            ps.setInt(1, failedAttempts);
            ps.setTimestamp(2, lockedUntil == null ? null : Timestamp.valueOf(lockedUntil));
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }
}
