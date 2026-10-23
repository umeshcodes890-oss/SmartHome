package com.smarthome.service;

import com.smarthome.model.AuthenticatedUser;
import com.smarthome.model.UserRole;
import com.smarthome.model.UserSummary;
import java.sql.SQLException;
import java.util.List;

public interface UserRepository {
    record Credentials(AuthenticatedUser user, String passwordHash, boolean enabled) {}

    Credentials findCredentials(String username) throws SQLException;
    AuthenticatedUser create(String username, String displayName, String passwordHash, UserRole role)
        throws SQLException;
    AuthenticatedUser createFirstAdmin(String username, String displayName, String passwordHash)
        throws SQLException;
    List<UserSummary> findAll() throws SQLException;
    void updateProfile(long userId, String displayName) throws SQLException;
    void updatePassword(long userId, String passwordHash) throws SQLException;
    void updateRole(long userId, UserRole role) throws SQLException;
    void setEnabled(long userId, boolean enabled) throws SQLException;
}
