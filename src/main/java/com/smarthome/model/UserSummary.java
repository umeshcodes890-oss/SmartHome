package com.smarthome.model;

public record UserSummary(long id, String username, String displayName, UserRole role, boolean enabled) {}
