package com.smarthome.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smarthome.model.AuthenticatedUser;
import com.smarthome.model.UserRole;
import com.smarthome.model.UserSummary;
import com.smarthome.dao.ActivityLogDao;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {
    private static final char[] PASSWORD = "correct horse battery".toCharArray();

    @Test
    void registrationHashesPasswordAndAllowsValidLoginOnly() throws SQLException {
        InMemoryUsers users = new InMemoryUsers();
        AuthenticationService authentication = new AuthenticationService(users);

        AuthenticatedUser registered = authentication.registerResident("resident.one", "Resident One", PASSWORD);
        AuthenticatedUser loggedIn = authentication.login("resident.one", PASSWORD);

        assertEquals(UserRole.RESIDENT, registered.role());
        assertEquals(registered, loggedIn);
        assertTrue(users.credentials.get("resident.one").passwordHash().startsWith("$argon2id$"));
        assertThrows(AuthenticationService.AuthenticationException.class,
            () -> authentication.login("resident.one", "incorrect password".toCharArray()));
    }

    @Test
    void registrationRejectsDuplicateNames() throws SQLException {
        InMemoryUsers users = new InMemoryUsers();
        AuthenticationService authentication = new AuthenticationService(users);
        authentication.registerResident("same.name", "First Account", PASSWORD);

        assertThrows(SQLException.class,
            () -> authentication.registerResident("same.name", "Duplicate Account", PASSWORD));
    }

    @Test
    void firstAdministratorBootstrapCreatesAdminAndRejectsSecondBootstrap() throws SQLException {
        InMemoryUsers users = new InMemoryUsers();
        AuthenticationService authentication = new AuthenticationService(users);

        AuthenticatedUser admin = authentication.bootstrapFirstAdmin("first.admin", "First Admin", PASSWORD);

        assertEquals(UserRole.ADMIN, admin.role());
        assertThrows(SQLException.class,
            () -> authentication.bootstrapFirstAdmin("second.admin", "Second Admin", PASSWORD));
    }

    @Test
    void changingPasswordRevokesPreviousSessionsButKeepsCurrentSessionValid() throws SQLException {
        InMemoryUsers users = new InMemoryUsers();
        AuthenticationService authentication = new AuthenticationService(users);
        AuthenticatedUser firstSession = authentication.registerResident(
            "password.user", "Password User", PASSWORD);

        AuthenticatedUser currentSession = authentication.changePassword(firstSession, PASSWORD,
            "replacement password".toCharArray());

        assertTrue(currentSession.sessionVersion() > firstSession.sessionVersion());
        authentication.assertActive(currentSession);
        assertThrows(SecurityException.class, () -> authentication.assertActive(firstSession));
        assertEquals(currentSession, authentication.login("password.user",
            "replacement password".toCharArray()));
    }

    @Test
    void residentCannotInvokeAdministratorOperationsThroughManagementService() {
        AuthenticatedUser resident = new AuthenticatedUser(7, "resident", "Resident", UserRole.RESIDENT);
        AuthenticationService authentication = new AuthenticationService(new InMemoryUsers());
        HomeManagementService management = new HomeManagementService(resident, authentication,
            null, null, null);

        assertThrows(SecurityException.class, () -> management.addRoom("Unauthorized room", 1));
        assertThrows(SecurityException.class, () -> management.addDevice("Unauthorized device", "LIGHT", 1,
            java.math.BigDecimal.valueOf(12)));
        assertThrows(SecurityException.class, management::findSchedules);
        assertThrows(SecurityException.class,
            () -> new ActivityLogService(new ActivityLogDao(), authentication)
                .findPage(resident, "", "ALL", null, null, 0, 50));
    }

    private static final class InMemoryUsers implements UserRepository {
        private final Map<String, Credentials> credentials = new HashMap<>();
        private long nextId = 1;

        @Override
        public Credentials findCredentials(String username) {
            return credentials.get(username.toLowerCase(Locale.ROOT));
        }

        @Override
        public AuthenticatedUser create(String username, String displayName, String passwordHash, UserRole role)
                throws SQLException {
            return save(username, displayName, passwordHash, role);
        }

        @Override
        public AuthenticatedUser createFirstAdmin(String username, String displayName, String passwordHash)
                throws SQLException {
            if (credentials.values().stream().anyMatch(c -> c.user().role() == UserRole.ADMIN)) {
                throw new SQLException("Administrator already exists.");
            }
            return save(username, displayName, passwordHash, UserRole.ADMIN);
        }

        private AuthenticatedUser save(String username, String displayName, String passwordHash, UserRole role)
                throws SQLException {
            String key = username.toLowerCase(Locale.ROOT);
            if (credentials.containsKey(key)) throw new SQLException("Duplicate username.");
            AuthenticatedUser user = new AuthenticatedUser(nextId++, username, displayName, role, 0);
            credentials.put(key, new Credentials(user, passwordHash, true));
            return user;
        }

        @Override public List<UserSummary> findAll() { return List.of(); }
        @Override public void updateProfile(long userId, String displayName) {}
        @Override public void updatePassword(long userId, String passwordHash) {
            credentials.replaceAll((key, value) -> value.user().id() == userId
                ? new Credentials(new AuthenticatedUser(value.user().id(), value.user().username(),
                    value.user().displayName(), value.user().role(), value.user().sessionVersion() + 1),
                    passwordHash, value.enabled())
                : value);
        }
        @Override public void updateRole(long targetId, UserRole role) {}
        @Override public void setEnabled(long targetId, boolean enabled) {}
    }
}
