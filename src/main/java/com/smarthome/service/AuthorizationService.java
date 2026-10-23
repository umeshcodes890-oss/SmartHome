package com.smarthome.service;

import com.smarthome.model.AuthenticatedUser;
import com.smarthome.model.UserRole;

public final class AuthorizationService {
    private final AuthenticatedUser user;

    public AuthorizationService(AuthenticatedUser user) {
        if (user == null) throw new SecurityException("Authentication is required.");
        this.user = user;
    }

    public AuthenticatedUser user() { return user; }

    public void requireAuthenticated() {
        if (user.id() <= 0) throw new SecurityException("Authentication is required.");
    }

    public void requireAdmin() {
        requireAuthenticated();
        if (user.role() != UserRole.ADMIN) throw new SecurityException("Administrator permission is required.");
    }

    public boolean isAdmin() {
        return user.role() == UserRole.ADMIN;
    }

    public boolean canViewPage(String page) {
        return switch (page) {
            case "Dashboard", "Devices", "Rooms", "Energy Usage", "Sensor Monitoring", "Settings" -> true;
            case "Schedules", "Activity Logs", "User Management", "Automation Rules" -> isAdmin();
            default -> false;
        };
    }
}
