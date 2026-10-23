package com.smarthome.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Database {
    private static final Map<String, String> DOT_ENV = loadDotEnv();

    private Database() {}

    public static Connection getConnection() throws SQLException {
        String url = setting("SMART_HOME_DB_URL",
            "jdbc:mysql://localhost:3306/smart_home_db?useSSL=false&serverTimezone=UTC");
        String user = setting("SMART_HOME_DB_USER", "root");
        String password = setting("SMART_HOME_DB_PASSWORD", "");
        if (password.isBlank()) {
            throw new SQLException("Database password is not configured. Set SMART_HOME_DB_PASSWORD in the environment or .env file.");
        }
        return DriverManager.getConnection(url, user, password);
    }

    private static String setting(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null) {
            value = DOT_ENV.get(key);
        }
        return value == null || value.isBlank() && !key.equals("SMART_HOME_DB_PASSWORD") ? fallback : value;
    }

    private static Map<String, String> loadDotEnv() {
        Path envFile = Path.of(System.getProperty("user.dir"), ".env");
        if (!Files.isRegularFile(envFile)) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ex) {
            throw new ExceptionInInitializerError("Could not read .env file: " + ex.getMessage());
        }
        return Map.copyOf(values);
    }
}