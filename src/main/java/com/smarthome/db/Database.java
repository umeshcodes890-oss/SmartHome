package com.smarthome.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public final class Database {
    private static final Map<String, String> DOT_ENV = loadDotEnv();

    private Database() {}

    public static Connection getConnection() throws SQLException {
        String url = setting("SMART_HOME_DB_URL",
            "jdbc:mysql://localhost:3306/smart_home_db?sslMode=DISABLED&serverTimezone=UTC");
        String user = setting("SMART_HOME_DB_USER", "");
        String password = setting("SMART_HOME_DB_PASSWORD", "");
        if (user.isBlank() || password.isBlank()) {
            throw new SQLException("Database credentials are not configured. Set SMART_HOME_DB_USER and SMART_HOME_DB_PASSWORD in the environment or .env file.");
        }
        validateTlsConfiguration(url);
        return DriverManager.getConnection(url, user, password);
    }

    static void validateTlsConfiguration(String url) throws SQLException {
        if (url == null || !url.startsWith("jdbc:mysql://")) return;
        URI uri;
        try {
            uri = new URI(url.substring("jdbc:".length()));
        } catch (URISyntaxException ex) {
            throw new SQLException("The configured MySQL URL is invalid.", ex);
        }
        String host = uri.getHost();
        if (host == null || isLocalHost(host)) return;
        String query = uri.getRawQuery();
        if (query != null) {
            for (String parameter : query.split("&")) {
                String[] entry = parameter.split("=", 2);
                if (entry.length == 2 && "sslMode".equalsIgnoreCase(entry[0])
                        && "VERIFY_IDENTITY".equalsIgnoreCase(entry[1])) return;
            }
        }
        throw new SQLException("Remote MySQL connections must set sslMode=VERIFY_IDENTITY in SMART_HOME_DB_URL.");
    }

    private static boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
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
