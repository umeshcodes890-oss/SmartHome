package com.smarthome.dao;

import com.smarthome.db.Database;
import com.smarthome.model.SensorAlert;
import com.smarthome.model.SensorAlertSettings;
import com.smarthome.model.SensorReading;
import com.smarthome.service.SensorAlertPolicy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SensorDao {
    private final ConnectionProvider connections;

    public SensorDao() {
        this(Database::getConnection);
    }

    SensorDao(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    public List<SensorReading> findLatest() throws SQLException {
        return findLatest(null, null);
    }

    public List<SensorReading> findLatest(Integer roomId, SensorReading.Type type) throws SQLException {
        String sql = "SELECT s.reading_id,s.room_id,r.room_name,s.sensor_type,s.reading_value," +
            "s.simulated,s.recorded_at FROM sensor_readings s JOIN rooms r ON r.room_id=s.room_id " +
            "JOIN (SELECT room_id,sensor_type,MAX(reading_id) AS reading_id FROM sensor_readings " +
            "GROUP BY room_id,sensor_type) latest ON latest.reading_id=s.reading_id " +
            "WHERE (? IS NULL OR s.room_id=?) AND (? IS NULL OR s.sensor_type=?) " +
            "ORDER BY r.room_name,s.sensor_type";
        List<SensorReading> readings = new ArrayList<>();
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindFilters(statement, roomId, type);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) readings.add(map(results));
            }
        }
        return List.copyOf(readings);
    }

    public List<SensorReading> findHistory(Integer roomId, SensorReading.Type type, int limit) throws SQLException {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("History limit must be between 1 and 1000.");
        String sql = "SELECT s.reading_id,s.room_id,r.room_name,s.sensor_type,s.reading_value," +
            "s.simulated,s.recorded_at FROM sensor_readings s JOIN rooms r ON r.room_id=s.room_id " +
            "WHERE (? IS NULL OR s.room_id=?) AND (? IS NULL OR s.sensor_type=?) " +
            "ORDER BY s.recorded_at DESC,s.reading_id DESC LIMIT ?";
        List<SensorReading> readings = new ArrayList<>();
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindFilters(statement, roomId, type);
            statement.setInt(5, limit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) readings.add(map(results));
            }
        }
        return List.copyOf(readings);
    }

    private static void bindFilters(PreparedStatement statement, Integer roomId, SensorReading.Type type)
            throws SQLException {
        if (roomId == null) {
            statement.setNull(1, Types.INTEGER);
            statement.setNull(2, Types.INTEGER);
        } else {
            statement.setInt(1, roomId);
            statement.setInt(2, roomId);
        }
        if (type == null) {
            statement.setNull(3, Types.VARCHAR);
            statement.setNull(4, Types.VARCHAR);
        } else {
            statement.setString(3, type.name());
            statement.setString(4, type.name());
        }
    }

    public List<SensorAlert> findAlerts(Integer roomId, int limit) throws SQLException {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("Alert limit must be between 1 and 500.");
        String sql = "SELECT a.alert_id,a.room_id,r.room_name,a.sensor_type,a.reading_value," +
            "a.threshold_value,a.simulated,a.triggered_at,a.resolved_at FROM sensor_alerts a " +
            "JOIN rooms r ON r.room_id=a.room_id WHERE (? IS NULL OR a.room_id=?) " +
            "ORDER BY a.triggered_at DESC,a.alert_id DESC LIMIT ?";
        List<SensorAlert> alerts = new ArrayList<>();
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (roomId == null) {
                statement.setNull(1, Types.INTEGER);
                statement.setNull(2, Types.INTEGER);
            } else {
                statement.setInt(1, roomId);
                statement.setInt(2, roomId);
            }
            statement.setInt(3, limit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    alerts.add(new SensorAlert(results.getLong("alert_id"), results.getInt("room_id"),
                        results.getString("room_name"), SensorReading.Type.valueOf(results.getString("sensor_type")),
                        results.getBigDecimal("reading_value"), results.getBigDecimal("threshold_value"),
                        results.getBoolean("simulated"), instant(results, "triggered_at"),
                        nullableInstant(results, "resolved_at")));
                }
            }
        }
        return List.copyOf(alerts);
    }

    public SensorAlertSettings findAlertSettings() throws SQLException {
        BigDecimal temperature = null;
        BigDecimal humidity = null;
        try (Connection connection = connections.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT sensor_type,threshold_value FROM sensor_alert_settings");
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                switch (results.getString("sensor_type")) {
                    case "TEMPERATURE" -> temperature = results.getBigDecimal("threshold_value");
                    case "HUMIDITY" -> humidity = results.getBigDecimal("threshold_value");
                    default -> throw new SQLException("Unexpected sensor alert setting type.");
                }
            }
        }
        if (temperature == null || humidity == null) {
            throw new SQLException("Sensor alert settings are incomplete. Apply the V8 sensor alerts migration.");
        }
        return new SensorAlertSettings(temperature, humidity);
    }

    public void saveAlertSettings(SensorAlertSettings settings) throws SQLException {
        Objects.requireNonNull(settings, "settings");
        try (Connection connection = connections.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO sensor_alert_settings(sensor_type,threshold_value) VALUES(?,?) " +
                    "ON DUPLICATE KEY UPDATE threshold_value=VALUES(threshold_value)")) {
                statement.setString(1, SensorReading.Type.TEMPERATURE.name());
                statement.setBigDecimal(2, settings.temperatureThreshold());
                statement.executeUpdate();
                statement.setString(1, SensorReading.Type.HUMIDITY.name());
                statement.setBigDecimal(2, settings.humidityThreshold());
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public void saveSimulated(List<SensorReading> readings) throws SQLException {
        if (readings == null || readings.isEmpty()) return;
        for (SensorReading reading : readings) {
            if (reading == null || !reading.simulated()) {
                throw new IllegalArgumentException("Simulator may only store simulated readings.");
            }
        }
        try (Connection connection = connections.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO sensor_readings(room_id,sensor_type,reading_value,simulated,recorded_at) " +
                    "VALUES(?,?,?,TRUE,?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                for (SensorReading reading : readings) {
                    statement.setInt(1, reading.roomId());
                    statement.setString(2, reading.type().name());
                    statement.setBigDecimal(3, reading.value());
                    statement.setObject(4, LocalDateTime.ofInstant(reading.recordedAt(), ZoneOffset.UTC));
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("MySQL did not return the inserted sensor reading ID.");
                        evaluateAlert(connection, reading, keys.getLong(1));
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    private static void evaluateAlert(Connection connection, SensorReading reading, long readingId) throws SQLException {
        if (reading.type() == SensorReading.Type.MOTION) return;
        BigDecimal threshold;
        try (PreparedStatement settings = connection.prepareStatement(
                "SELECT threshold_value FROM sensor_alert_settings WHERE sensor_type=?")) {
            settings.setString(1, reading.type().name());
            try (ResultSet results = settings.executeQuery()) {
                if (!results.next()) throw new SQLException("Missing sensor alert threshold for " + reading.type() + ".");
                threshold = results.getBigDecimal(1);
            }
        }
        try (PreparedStatement initialize = connection.prepareStatement(
                "INSERT IGNORE INTO sensor_alert_state(room_id,sensor_type,active_alert_id) VALUES(?,?,NULL)")) {
            initialize.setInt(1, reading.roomId());
            initialize.setString(2, reading.type().name());
            initialize.executeUpdate();
        }
        Long activeAlertId;
        try (PreparedStatement state = connection.prepareStatement(
                "SELECT active_alert_id FROM sensor_alert_state WHERE room_id=? AND sensor_type=? FOR UPDATE")) {
            state.setInt(1, reading.roomId());
            state.setString(2, reading.type().name());
            try (ResultSet results = state.executeQuery()) {
                if (!results.next()) throw new SQLException("Sensor alert state could not be initialized.");
                long value = results.getLong(1);
                activeAlertId = results.wasNull() ? null : value;
            }
        }
        boolean exceeded = SensorAlertPolicy.exceedsThreshold(reading.type(), reading.value(), threshold);
        if (SensorAlertPolicy.shouldRaise(activeAlertId != null, exceeded)) {
            long alertId;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO sensor_alerts(room_id,sensor_type,reading_id,reading_value,threshold_value," +
                    "simulated,triggered_at) VALUES(?,?,?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                insert.setInt(1, reading.roomId());
                insert.setString(2, reading.type().name());
                insert.setLong(3, readingId);
                insert.setBigDecimal(4, reading.value());
                insert.setBigDecimal(5, threshold);
                insert.setBoolean(6, reading.simulated());
                insert.setObject(7, LocalDateTime.ofInstant(reading.recordedAt(), ZoneOffset.UTC));
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("MySQL did not return the inserted sensor alert ID.");
                    alertId = keys.getLong(1);
                }
            }
            updateActiveAlert(connection, reading.roomId(), reading.type(), alertId);
        } else if (activeAlertId != null && !exceeded) {
            try (PreparedStatement resolve = connection.prepareStatement(
                    "UPDATE sensor_alerts SET resolved_at=? WHERE alert_id=? AND resolved_at IS NULL")) {
                resolve.setObject(1, LocalDateTime.ofInstant(reading.recordedAt(), ZoneOffset.UTC));
                resolve.setLong(2, activeAlertId);
                resolve.executeUpdate();
            }
            updateActiveAlert(connection, reading.roomId(), reading.type(), null);
        }
    }

    private static void updateActiveAlert(Connection connection, int roomId, SensorReading.Type type,
            Long alertId) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE sensor_alert_state SET active_alert_id=? WHERE room_id=? AND sensor_type=?")) {
            if (alertId == null) update.setNull(1, Types.BIGINT);
            else update.setLong(1, alertId);
            update.setInt(2, roomId);
            update.setString(3, type.name());
            update.executeUpdate();
        }
    }

    private static void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static Instant instant(ResultSet results, String column) throws SQLException {
        LocalDateTime value = results.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static Instant nullableInstant(ResultSet results, String column) throws SQLException {
        return instant(results, column);
    }

    private static SensorReading map(ResultSet results) throws SQLException {
        return new SensorReading(results.getLong("reading_id"), results.getInt("room_id"),
            results.getString("room_name"), SensorReading.Type.valueOf(results.getString("sensor_type")),
            results.getBigDecimal("reading_value"), results.getBoolean("simulated"),
            instant(results, "recorded_at"));
    }

    static SensorReading map(ResultSet results, String roomName) throws SQLException {
        return new SensorReading(results.getLong("reading_id"), results.getInt("room_id"),
            roomName, SensorReading.Type.valueOf(results.getString("sensor_type")),
            results.getBigDecimal("reading_value"), results.getBoolean("simulated"),
            instant(results, "recorded_at"));
    }
}
