package com.smarthome.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {
    private static final String URL = System.getenv().getOrDefault(
        "SMART_HOME_DB_URL", "jdbc:mysql://localhost:3306/smart_home_db?useSSL=false&serverTimezone=UTC");
    private static final String USER = System.getenv().getOrDefault("SMART_HOME_DB_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("SMART_HOME_DB_PASSWORD", "Your_Password");

    private Database() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
