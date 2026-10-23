package com.smarthome.service;

import com.smarthome.dao.UserDao;
import com.smarthome.model.AuthenticatedUser;
import com.smarthome.model.UserRole;
import com.smarthome.model.UserSummary;
import java.sql.SQLException;
import java.util.List;

public final class AuthenticationService {
    private final UserRepository users;
    private final PasswordHasher hasher;

    public AuthenticationService(UserRepository users) {
        this(users, new PasswordHasher());
    }

    AuthenticationService(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    public AuthenticatedUser login(String username, char[] password) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            throw new AuthenticationException("Incorrect username or password.");
        }
        UserRepository.Credentials credentials = users.findCredentials(username.trim());
        if (credentials == null || !credentials.enabled()
                || !hasher.verify(password, credentials.passwordHash())) {
            throw new AuthenticationException("Incorrect username or password.");
        }
        return credentials.user();
    }

    public AuthenticatedUser registerResident(String username, String displayName, char[] password)
            throws SQLException {
        validateProfile(username, displayName);
        validatePassword(password);
        return users.create(username.trim(), displayName.trim(), hasher.hash(password), UserRole.RESIDENT);
    }

    public AuthenticatedUser createUser(AuthorizationService authorization, String username, String displayName,
            char[] password, UserRole role) throws SQLException {
        authorization.requireAdmin();
        assertActive(authorization.user());
        validateProfile(username, displayName);
        validatePassword(password);
        if (role == null) throw new IllegalArgumentException("Select an account role.");
        return users.create(username.trim(), displayName.trim(), hasher.hash(password), role);
    }

    public AuthenticatedUser bootstrapFirstAdmin(String username, String displayName, char[] password)
            throws SQLException {
        validateProfile(username, displayName);
        validatePassword(password);
        return users.createFirstAdmin(username.trim(), displayName.trim(), hasher.hash(password));
    }

    public List<UserSummary> listUsers(AuthorizationService authorization) throws SQLException {
        authorization.requireAdmin();
        assertActive(authorization.user());
        return users.findAll();
    }

    public void updateUserRole(AuthorizationService authorization, long targetId, UserRole role)
            throws SQLException {
        authorization.requireAdmin();
        assertActive(authorization.user());
        if (role == null) throw new IllegalArgumentException("Select an account role.");
        if (targetId == authorization.user().id() && role != UserRole.ADMIN) {
            throw new IllegalArgumentException("You cannot remove your own administrator role.");
        }
        users.updateRole(targetId, role);
    }

    public void setUserEnabled(AuthorizationService authorization, long targetId, boolean enabled)
            throws SQLException {
        authorization.requireAdmin();
        assertActive(authorization.user());
        if (targetId == authorization.user().id() && !enabled) {
            throw new IllegalArgumentException("You cannot disable your own account.");
        }
        users.setEnabled(targetId, enabled);
    }

    public AuthenticatedUser updateProfile(AuthenticatedUser current, String displayName) throws SQLException {
        assertActive(current);
        validateProfile(current.username(), displayName);
        users.updateProfile(current.id(), displayName.trim());
        return new AuthenticatedUser(current.id(), current.username(), displayName.trim(),
            current.role(), current.sessionVersion());
    }

    public AuthenticatedUser changePassword(AuthenticatedUser current, char[] currentPassword, char[] newPassword)
            throws SQLException {
        UserRepository.Credentials credentials = users.findCredentials(current.username());
        if (credentials == null || !credentials.enabled()
                || credentials.user().id() != current.id()
                || credentials.user().sessionVersion() != current.sessionVersion()
                || !hasher.verify(currentPassword, credentials.passwordHash())) {
            throw new AuthenticationException("Current password is incorrect.");
        }
        validatePassword(newPassword);
        users.updatePassword(current.id(), hasher.hash(newPassword));
        UserRepository.Credentials updated = users.findCredentials(current.username());
        if (updated == null || !updated.enabled()) {
            throw new AuthenticationException("This account is no longer authorized. Sign in again.");
        }
        return updated.user();
    }

    public void assertActive(AuthenticatedUser user) throws SQLException {
        if (user == null) throw new SecurityException("Authentication is required.");
        UserRepository.Credentials credentials = users.findCredentials(user.username());
        if (credentials == null || !credentials.enabled() || credentials.user().id() != user.id()
                || credentials.user().role() != user.role()
                || credentials.user().sessionVersion() != user.sessionVersion()) {
            throw new SecurityException("This account is no longer authorized. Sign in again.");
        }
    }

    private static void validateProfile(String username, String displayName) {
        if (username == null || !username.matches("[A-Za-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException("Username must be 3–50 letters, numbers, dots, underscores, or hyphens.");
        }
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 100) {
            throw new IllegalArgumentException("Display name is required and must be at most 100 characters.");
        }
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < 12 || password.length > 128) {
            throw new IllegalArgumentException("Password must contain between 12 and 128 characters.");
        }
    }

    public static final class AuthenticationException extends SecurityException {
        private static final long serialVersionUID = 1L;
        public AuthenticationException(String message) { super(message); }
    }
}
