package com.smarthome.dao;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
interface ConnectionProvider {
    Connection getConnection() throws SQLException;
}
