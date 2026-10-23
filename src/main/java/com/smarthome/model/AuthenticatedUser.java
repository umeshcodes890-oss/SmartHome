package com.smarthome.model;

public record AuthenticatedUser(long id, String username, String displayName, UserRole role, int sessionVersion) {
    public AuthenticatedUser(long id, String username, String displayName, UserRole role) {
        this(id, username, displayName, role, 0);
    }
}
