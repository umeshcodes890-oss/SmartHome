package com.smarthome.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class DatabaseTest {
    @Test
    void allowsLocalMysqlWithoutTls() {
        assertDoesNotThrow(() -> Database.validateTlsConfiguration(
            "jdbc:mysql://localhost:3306/smart_home_db?sslMode=DISABLED"));
        assertDoesNotThrow(() -> Database.validateTlsConfiguration(
            "jdbc:mysql://127.0.0.1:3306/smart_home_db"));
    }

    @Test
    void requiresVerifiedTlsForRemoteMysql() {
        assertThrows(SQLException.class, () -> Database.validateTlsConfiguration(
            "jdbc:mysql://db.example.net:3306/smart_home_db?sslMode=PREFERRED"));
        assertDoesNotThrow(() -> Database.validateTlsConfiguration(
            "jdbc:mysql://db.example.net:3306/smart_home_db?sslMode=VERIFY_IDENTITY"));
    }
}
