package com.smarthome.dao;

import com.smarthome.db.Database;
import com.smarthome.model.AuthenticatedUser;
import com.smarthome.model.UserRole;
import com.smarthome.model.UserSummary;
import com.smarthome.service.UserRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;

public final class UserDao implements UserRepository {
    @Override public Credentials findCredentials(String username) throws SQLException {
        String sql = "SELECT user_id,username,display_name,role,password_hash,enabled,session_version " +
            "FROM app_users WHERE username=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) return null;
                AuthenticatedUser user = new AuthenticatedUser(results.getLong("user_id"),
                    results.getString("username"), results.getString("display_name"),
                    UserRole.valueOf(results.getString("role")), results.getInt("session_version"));
                return new Credentials(user, results.getString("password_hash"), results.getBoolean("enabled"));
            }
        }
    }

    @Override public AuthenticatedUser create(String username, String displayName, String passwordHash,
            UserRole role) throws SQLException {
        String sql = "INSERT INTO app_users(username,display_name,password_hash,role,enabled) VALUES(?,?,?,?,TRUE)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, displayName);
            statement.setString(3, passwordHash);
            statement.setString(4, role.name());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("User was created but its identifier could not be retrieved.");
                return new AuthenticatedUser(keys.getLong(1), username, displayName, role);
            }
        } catch (SQLIntegrityConstraintViolationException ex) {
            throw new DuplicateUsernameException();
        }
    }

    @Override public AuthenticatedUser createFirstAdmin(String username, String displayName, String passwordHash)
            throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            boolean lockAcquired = false;
            try {
                lockAcquired = acquireAdminLock(connection);
                if (!lockAcquired) throw new SQLException("Administrator bootstrap is busy. Please retry.");
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT user_id FROM app_users WHERE role='ADMIN' LIMIT 1 FOR UPDATE");
                     ResultSet results = statement.executeQuery()) {
                    if (results.next()) {
                        throw new IllegalStateException("An administrator already exists.");
                    }
                }
                AuthenticatedUser user = insert(connection, username, displayName, passwordHash, UserRole.ADMIN);
                connection.commit();
                return user;
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            } finally {
                if (lockAcquired) releaseAdminLock(connection);
            }
        }
    }

    @Override public List<UserSummary> findAll() throws SQLException {
        List<UserSummary> users = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id,username,display_name,role,enabled FROM app_users ORDER BY username");
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                users.add(new UserSummary(results.getLong("user_id"), results.getString("username"),
                    results.getString("display_name"), UserRole.valueOf(results.getString("role")),
                    results.getBoolean("enabled")));
            }
        }
        return List.copyOf(users);
    }

    @Override public void updateProfile(long userId, String displayName) throws SQLException {
        updateOne("UPDATE app_users SET display_name=? WHERE user_id=?", displayName, userId,
            "User profile not found.");
    }

    @Override public void updatePassword(long userId, String passwordHash) throws SQLException {
        updateOne("UPDATE app_users SET password_hash=?,session_version=session_version+1 WHERE user_id=?",
            passwordHash, userId,
            "User account not found.");
    }

    @Override public void updateRole(long userId, UserRole role) throws SQLException {
        withAdminLock(connection -> {
            UserSummary user = findSummary(connection, userId, true);
            if (user == null) throw new SQLException("User account not found.");
            if (user.enabled() && user.role() == UserRole.ADMIN && role != UserRole.ADMIN
                    && countEnabledAdmins(connection) <= 1) {
                throw new IllegalStateException("The last enabled administrator cannot be demoted.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE app_users SET role=? WHERE user_id=?")) {
                statement.setString(1, role.name());
                statement.setLong(2, userId);
                statement.executeUpdate();
            }
        });
    }

    @Override public void setEnabled(long userId, boolean enabled) throws SQLException {
        withAdminLock(connection -> {
            UserSummary user = findSummary(connection, userId, true);
            if (user == null) throw new SQLException("User account not found.");
            if (user.enabled() && user.role() == UserRole.ADMIN && !enabled
                    && countEnabledAdmins(connection) <= 1) {
                throw new IllegalStateException("The last enabled administrator cannot be disabled.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE app_users SET enabled=? WHERE user_id=?")) {
                statement.setBoolean(1, enabled);
                statement.setLong(2, userId);
                statement.executeUpdate();
            }
        });
    }

    private void updateOne(String sql, String value, long userId, String missingMessage) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setLong(2, userId);
            if (statement.executeUpdate() != 1) {
                try (PreparedStatement exists = connection.prepareStatement(
                        "SELECT 1 FROM app_users WHERE user_id=?")) {
                    exists.setLong(1, userId);
                    try (ResultSet results = exists.executeQuery()) {
                        if (!results.next()) throw new SQLException(missingMessage);
                    }
                }
            }
        }
    }

    private AuthenticatedUser insert(Connection connection, String username, String displayName,
                String passwordHash, UserRole role) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO app_users(username,display_name,password_hash,role,enabled) VALUES(?,?,?,?,TRUE)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, username);
                statement.setString(2, displayName);
                statement.setString(3, passwordHash);
                statement.setString(4, role.name());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("User was created but its identifier could not be retrieved.");
                    return new AuthenticatedUser(keys.getLong(1), username, displayName, role);
                }
            } catch (SQLIntegrityConstraintViolationException ex) {
                throw new DuplicateUsernameException();
            }
        }

    private UserSummary findSummary(Connection connection, long userId, boolean lock) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT user_id,username,display_name,role,enabled FROM app_users WHERE user_id=?"
                        + (lock ? " FOR UPDATE" : ""))) {
                statement.setLong(1, userId);
                try (ResultSet results = statement.executeQuery()) {
                    if (!results.next()) return null;
                    return new UserSummary(results.getLong("user_id"), results.getString("username"),
                        results.getString("display_name"), UserRole.valueOf(results.getString("role")),
                        results.getBoolean("enabled"));
                }
            }
        }

    private long countEnabledAdmins(Connection connection) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM app_users WHERE role='ADMIN' AND enabled=TRUE");
                 ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getLong(1);
            }
        }

    private void withAdminLock(SqlWork work) throws SQLException {
            try (Connection connection = Database.getConnection()) {
                connection.setAutoCommit(false);
                boolean lockAcquired = false;
                try {
                    lockAcquired = acquireAdminLock(connection);
                    if (!lockAcquired) throw new SQLException("Administrator account management is busy. Retry.");
                    work.run(connection);
                    connection.commit();
                } catch (SQLException | RuntimeException ex) {
                    rollback(connection, ex);
                    throw ex;
                } finally {
                    if (lockAcquired) releaseAdminLock(connection);
                }
            }
        }

    private boolean acquireAdminLock(Connection connection) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?,10)")) {
                statement.setString(1, "smart-home.admin-accounts");
                try (ResultSet results = statement.executeQuery()) {
                    return results.next() && results.getInt(1) == 1;
                }
            }
        }

    private void releaseAdminLock(Connection connection) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, "smart-home.admin-accounts");
                statement.executeQuery().close();
            }
        }

    private void rollback(Connection connection, Exception failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }

    @FunctionalInterface private interface SqlWork {
        void run(Connection connection) throws SQLException;
    }

    public static final class DuplicateUsernameException extends SQLException {
        private static final long serialVersionUID = 1L;
        public DuplicateUsernameException() { super("That username is already registered."); }
    }
}
