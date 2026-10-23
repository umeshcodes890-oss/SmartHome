package com.smarthome.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smarthome.model.SensorReading;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SensorDaoTest {
    @Test
    void savesGeneratedReadingsInOneTransactionAndMarksThemSimulated() throws SQLException {
        FakeDatabase database = new FakeDatabase();
        SensorDao dao = new SensorDao(database::connect);
        Instant recordedAt = Instant.parse("2026-10-23T08:00:00Z");

        dao.saveSimulated(List.of(
            reading(SensorReading.Type.TEMPERATURE, "20.5", recordedAt),
            reading(SensorReading.Type.HUMIDITY, "45.0", recordedAt),
            reading(SensorReading.Type.MOTION, "0", recordedAt)));

        assertEquals(3, database.readingInserts);
        assertEquals(0, database.alertInserts);
        assertEquals(1, database.commits);
        assertEquals(0, database.rollbacks);
        assertTrue(database.readingInsertSql.contains("simulated,recorded_at"));
        assertTrue(database.readingInsertSql.contains("VALUES(?,?,?,TRUE,?)"));
        assertTrue(database.connectionClosed);
        assertEquals(database.preparedStatements, database.closedStatements);
    }

    @Test
    void rejectsPhysicalReadingsBeforeOpeningAConnection() {
        int[] connections = {0};
        SensorDao dao = new SensorDao(() -> {
            connections[0]++;
            throw new SQLException("Unexpected connection.");
        });
        SensorReading physical = new SensorReading(0, 1, "Living Room",
            SensorReading.Type.TEMPERATURE, BigDecimal.TEN, false, Instant.now());

        assertThrows(IllegalArgumentException.class, () -> dao.saveSimulated(List.of(physical)));
        assertEquals(0, connections[0]);
    }

    @Test
    void recordsOnlyOneActiveAlertUntilTheReadingReturnsToThreshold() throws SQLException {
        FakeDatabase database = new FakeDatabase();
        SensorDao dao = new SensorDao(database::connect);
        Instant at = Instant.parse("2026-10-23T08:00:00Z");

        dao.saveSimulated(List.of(reading(SensorReading.Type.TEMPERATURE, "30.01", at)));
        dao.saveSimulated(List.of(reading(SensorReading.Type.TEMPERATURE, "31.00", at.plusSeconds(30))));
        assertEquals(1, database.alertInserts);
        assertEquals(0, database.resolvedAlerts);

        dao.saveSimulated(List.of(reading(SensorReading.Type.TEMPERATURE, "30.00", at.plusSeconds(60))));
        assertEquals(1, database.resolvedAlerts);
        dao.saveSimulated(List.of(reading(SensorReading.Type.TEMPERATURE, "30.01", at.plusSeconds(90))));
        assertEquals(2, database.alertInserts);
    }

    private static SensorReading reading(SensorReading.Type type, String value, Instant at) {
        return new SensorReading(0, 1, "Living Room", type, new BigDecimal(value), true, at);
    }

    private static final class FakeDatabase {
        private int readingInserts;
        private int alertInserts;
        private int nextReadingId = 10;
        private int nextAlertId = 100;
        private int commits;
        private int rollbacks;
        private int preparedStatements;
        private int closedStatements;
        private boolean connectionClosed;
        private String readingInsertSql = "";
        private Long activeAlertId;
        private int resolvedAlerts;

        private Connection connect() {
            return proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> prepare((String) args[0]);
                case "setAutoCommit", "commit" -> {
                    if ("commit".equals(method.getName())) commits++;
                    yield null;
                }
                case "rollback" -> {
                    rollbacks++;
                    yield null;
                }
                case "close" -> {
                    connectionClosed = true;
                    yield null;
                }
                case "isClosed" -> connectionClosed;
                case "toString" -> "FakeSensorConnection";
                default -> defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement prepare(String sql) {
            preparedStatements++;
            Map<Integer, Object> parameters = new HashMap<>();
            return proxy(PreparedStatement.class, (object, method, args) -> switch (method.getName()) {
                case "setString", "setInt", "setLong", "setBoolean", "setBigDecimal", "setObject", "setNull" -> {
                    parameters.put((Integer) args[0], "setNull".equals(method.getName()) ? null : args[1]);
                    yield null;
                }
                case "executeUpdate" -> execute(sql, parameters);
                case "executeQuery" -> query(sql, parameters);
                case "getGeneratedKeys" -> rows(new Object[]{
                    sql.startsWith("INSERT INTO sensor_readings") ? nextReadingId++ : nextAlertId++});
                case "close" -> {
                    closedStatements++;
                    yield null;
                }
                case "toString" -> sql;
                default -> defaultValue(method.getReturnType());
            });
        }

        private int execute(String sql, Map<Integer, Object> parameters) {
            if (sql.startsWith("INSERT INTO sensor_readings")) {
                readingInserts++;
                readingInsertSql = sql;
            } else if (sql.startsWith("INSERT INTO sensor_alerts")) {
                alertInserts++;
            } else if (sql.startsWith("UPDATE sensor_alert_state")) {
                activeAlertId = (Long) parameters.get(1);
            } else if (sql.startsWith("UPDATE sensor_alerts")) {
                resolvedAlerts++;
            }
            return 1;
        }

        private ResultSet query(String sql, Map<Integer, Object> parameters) {
            if (sql.startsWith("SELECT threshold_value")) {
                BigDecimal value = "TEMPERATURE".equals(parameters.get(1))
                    ? new BigDecimal("30.00") : new BigDecimal("70.00");
                return rows(new Object[]{value});
            }
            if (sql.startsWith("SELECT active_alert_id")) return rows(new Object[]{activeAlertId});
            return rows();
        }

        private static ResultSet rows(Object[]... values) {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
                case "next" -> ++index[0] < values.length;
                case "getBigDecimal" -> (BigDecimal) value(values, index[0], args[0], wasNull);
                case "getLong" -> {
                    Object result = value(values, index[0], args[0], wasNull);
                    yield result == null ? 0L : ((Number) result).longValue();
                }
                case "wasNull" -> wasNull[0];
                case "close" -> null;
                default -> defaultValue(method.getReturnType());
            });
        }

        private static Object value(Object[][] rows, int row, Object column, boolean[] wasNull) {
            int columnIndex = column instanceof Integer number ? number - 1 : 0;
            Object value = rows[row][columnIndex];
            wasNull[0] = value == null;
            return value;
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
