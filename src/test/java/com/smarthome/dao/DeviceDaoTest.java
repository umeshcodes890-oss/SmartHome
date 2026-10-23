package com.smarthome.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeviceDaoTest {
    @Test
    void createsUpdatesDeletesAndChangesDeviceStatusWithAuditAndUsageHistory() throws SQLException {
        FakeDatabase database = new FakeDatabase();
        DeviceDao dao = new DeviceDao(database::connect);

        dao.add("Porch's light", "LIGHT", 4, "OFF", new BigDecimal("14.50"), 23L);
        assertEquals("Porch's light", database.devices.get(1).name());
        assertEquals("DEVICE_CREATED", database.auditEvents.getFirst());
        assertEquals(1, database.commits);

        dao.update(1, "Porch light", "SMART_LIGHT", 4, new BigDecimal("18.25"), 23L);
        assertEquals("Porch light", database.devices.get(1).name());
        assertEquals("SMART_LIGHT", database.devices.get(1).type());
        assertEquals(new BigDecimal("18.25"), database.devices.get(1).watts());
        assertEquals("DEVICE_UPDATED", database.auditEvents.getLast());

        dao.setStatus(1, "ON", 23L);
        assertEquals("ON", database.devices.get(1).status());
        assertTrue(database.usageSessionOpen);
        assertEquals(1, database.statusHistoryRows);

        dao.setStatus(1, "OFF", 23L);
        assertEquals("OFF", database.devices.get(1).status());
        assertFalse(database.usageSessionOpen);
        assertEquals(2, database.statusHistoryRows);

        dao.delete(1, 23L);
        assertFalse(database.devices.containsKey(1));
        assertEquals("DEVICE_DELETED", database.auditEvents.getLast());
        assertEquals(5, database.commits);
    }

    @Test
    void rollsBackStatusAndHistoryWhenAuditWriteFails() {
        FakeDatabase database = new FakeDatabase();
        DeviceDao dao = new DeviceDao(database::connect);
        assertDoesNotThrow(() -> dao.add("Lamp", "LIGHT", 4, "OFF", BigDecimal.TEN));

        database.failAuditInsert = true;
        assertThrows(SQLException.class, () -> dao.setStatus(1, "ON"));

        assertEquals("OFF", database.devices.get(1).status());
        assertFalse(database.usageSessionOpen);
        assertEquals(1, database.rollbacks);
    }

    @Test
    void bulkStatusRuntimeFailureRollsBackEveryMutation() throws SQLException {
        FakeDatabase database = new FakeDatabase();
        DeviceDao dao = new DeviceDao(database::connect);
        dao.add("Lamp", "LIGHT", 4, "OFF", BigDecimal.TEN);
        database.failStatusHistory = true;

        assertThrows(IllegalStateException.class, () -> dao.setAllStatus("ON"));

        assertEquals("OFF", database.devices.get(1).status());
        assertFalse(database.usageSessionOpen);
        assertEquals(0, database.statusHistoryRows);
        assertEquals(1, database.rollbacks);
    }

    @Test
    void rejectsInvalidInputBeforeConnectingAndPropagatesConnectionFailures() {
        int[] connectionAttempts = {0};
        DeviceDao noConnection = new DeviceDao(() -> {
            connectionAttempts[0]++;
            throw new SQLException("database offline");
        });
        assertThrows(IllegalArgumentException.class,
            () -> noConnection.add(" ", "LIGHT", 4, "OFF", BigDecimal.TEN));
        assertEquals(0, connectionAttempts[0]);
        assertThrows(SQLException.class,
            () -> noConnection.add("Lamp", "LIGHT", 4, "OFF", BigDecimal.TEN));
        assertEquals(1, connectionAttempts[0]);
    }

    private static final class FakeDatabase {
        private final Map<Integer, DeviceRow> devices = new HashMap<>();
        private final List<String> auditEvents = new ArrayList<>();
        private int nextId = 1;
        private int lastId;
        private int commits;
        private int rollbacks;
        private int statusHistoryRows;
        private boolean usageSessionOpen;
        private boolean failAuditInsert;
        private boolean failStatusHistory;

        private Connection connect() {
            ConnectionState state = new ConnectionState();
            return proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> statement((String) args[0], state);
                case "getAutoCommit" -> state.autoCommit;
                case "setAutoCommit" -> {
                    boolean nextAutoCommit = (boolean) args[0];
                    if (!nextAutoCommit && state.autoCommit) state.snapshot = snapshotState();
                    state.autoCommit = nextAutoCommit;
                    yield null;
                }
                case "commit" -> {
                    commits++;
                    state.snapshot = null;
                    yield null;
                }
                case "rollback" -> {
                    rollbacks++;
                    restore(state.snapshot);
                    state.snapshot = null;
                    yield null;
                }
                case "close" -> {
                    state.closed = true;
                    yield null;
                }
                case "isClosed" -> state.closed;
                case "isWrapperFor" -> false;
                case "unwrap" -> throw new SQLException("Not a wrapper.");
                case "toString" -> "FakeConnection";
                default -> defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement statement(String sql, ConnectionState connection) {
            Map<Integer, Object> parameters = new HashMap<>();
            return proxy(PreparedStatement.class, (object, method, args) -> switch (method.getName()) {
                case "setString", "setInt", "setLong", "setBoolean", "setBigDecimal", "setObject" -> {
                    parameters.put((Integer) args[0], args[1]);
                    yield null;
                }
                case "setNull" -> {
                    parameters.put((Integer) args[0], null);
                    yield null;
                }
                case "executeQuery" -> resultSet(query(sql, parameters));
                case "executeUpdate" -> execute(sql, parameters);
                case "close", "clearParameters" -> null;
                case "toString" -> sql;
                default -> defaultValue(method.getReturnType());
            });
        }

        private List<Object[]> query(String sql, Map<Integer, Object> p) throws SQLException {
            if (sql.startsWith("SELECT GET_LOCK")) return java.util.Collections.singletonList(new Object[]{1});
            if (sql.startsWith("SELECT LAST_INSERT_ID")) return java.util.Collections.singletonList(new Object[]{lastId});
            if (sql.contains("LOWER(device_name)")) {
                boolean duplicate = devices.values().stream().anyMatch(row ->
                    row.id() != (Integer) p.get(2) && row.name().equalsIgnoreCase((String) p.get(1)));
                return duplicate ? java.util.Collections.singletonList(new Object[]{1}) : List.of();
            }
            if (sql.contains("FROM devices d JOIN rooms r") && sql.contains("WHERE d.device_id=?")) {
                DeviceRow row = devices.get((Integer) p.get(1));
                return row == null ? List.of() : java.util.Collections.singletonList(snapshot(row));
            }
            if (sql.startsWith("SELECT 1 FROM devices")) {
                return devices.containsKey((Integer) p.get(1))
                    ? java.util.Collections.singletonList(new Object[]{1}) : List.of();
            }
            if (sql.contains("FROM devices d JOIN rooms r") && sql.contains("WHERE d.status<>?")) {
                return devices.values().stream().filter(row -> !row.status().equals(p.get(1)))
                    .map(FakeDatabase::snapshot).toList();
            }
            return List.of();
        }

        private int execute(String sql, Map<Integer, Object> p) throws SQLException {
            if (sql.startsWith("INSERT INTO devices")) {
                lastId = nextId++;
                devices.put(lastId, new DeviceRow(lastId, (String) p.get(1), (String) p.get(2),
                    (Integer) p.get(3), "Room", (String) p.get(4), (BigDecimal) p.get(5)));
                return 1;
            }
            if (sql.startsWith("UPDATE devices SET device_name")) {
                DeviceRow current = devices.get((Integer) p.get(5));
                if (current == null) return 0;
                devices.put(current.id(), new DeviceRow(current.id(), (String) p.get(1), (String) p.get(2),
                    (Integer) p.get(3), current.roomName(), current.status(), (BigDecimal) p.get(4)));
                return 1;
            }
            if (sql.startsWith("UPDATE devices SET status")) {
                DeviceRow current = devices.get((Integer) p.get(2));
                if (current == null) return 0;
                devices.put(current.id(), new DeviceRow(current.id(), current.name(), current.type(),
                    current.roomId(), current.roomName(), (String) p.get(1), current.watts()));
                return 1;
            }
            if (sql.startsWith("UPDATE energy_usage_sessions")) {
                if (!usageSessionOpen) return 0;
                usageSessionOpen = false;
                return 1;
            }
            if (sql.startsWith("INSERT INTO energy_usage_sessions")) {
                usageSessionOpen = true;
                return 1;
            }
            if (sql.startsWith("INSERT INTO device_status_history")) {
                if (failStatusHistory) throw new IllegalStateException("simulated history failure");
                statusHistoryRows++;
                return 1;
            }
            if (sql.startsWith("INSERT INTO activity_logs")) {
                if (failAuditInsert) throw new SQLException("simulated audit storage failure");
                auditEvents.add((String) p.get(1));
                return 1;
            }
            if (sql.startsWith("DELETE FROM devices")) return devices.remove((Integer) p.get(1)) == null ? 0 : 1;
            return 1;
        }

        private ResultSet resultSet(List<Object[]> rows) {
            int[] index = {-1};
            Object[] last = {null};
            return proxy(ResultSet.class, (object, method, args) -> {
                switch (method.getName()) {
                    case "next" -> {
                        index[0]++;
                        return index[0] < rows.size();
                    }
                    case "getInt", "getLong", "getString", "getBigDecimal" -> {
                        int column = ((Number) args[0]).intValue() - 1;
                        Object value = rows.get(index[0])[column];
                        last[0] = value;
                        if (method.getName().equals("getString")) return value == null ? null : value.toString();
                        if (method.getName().equals("getBigDecimal")) return value;
                        if (value instanceof Number number) {
                            if (method.getName().equals("getInt")) return Integer.valueOf(number.intValue());
                            return Long.valueOf(number.longValue());
                        }
                        if (method.getName().equals("getInt")) return Integer.valueOf(0);
                        return Long.valueOf(0);
                    }
                    case "wasNull" -> { return last[0] == null; }
                    case "close" -> { return null; }
                    case "toString" -> { return "FakeResultSet"; }
                    default -> { return defaultValue(method.getReturnType()); }
                }
            });
        }

        private static Object[] snapshot(DeviceRow row) {
            return new Object[]{row.id(), row.name(), row.type(), row.roomId(), row.roomName(),
                row.status(), row.watts()};
        }

        private Snapshot snapshotState() {
            return new Snapshot(new HashMap<>(devices), new ArrayList<>(auditEvents), nextId, lastId,
                statusHistoryRows, usageSessionOpen);
        }

        private void restore(Snapshot snapshot) {
            if (snapshot == null) return;
            devices.clear();
            devices.putAll(snapshot.devices());
            auditEvents.clear();
            auditEvents.addAll(snapshot.auditEvents());
            nextId = snapshot.nextId();
            lastId = snapshot.lastId();
            statusHistoryRows = snapshot.statusHistoryRows();
            usageSessionOpen = snapshot.usageSessionOpen();
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) return null;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0f;
            return 0d;
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
        }

        private record DeviceRow(int id, String name, String type, int roomId, String roomName,
                String status, BigDecimal watts) {}

        private record Snapshot(Map<Integer, DeviceRow> devices, List<String> auditEvents, int nextId,
                int lastId, int statusHistoryRows, boolean usageSessionOpen) {}

        private static final class ConnectionState {
            private boolean autoCommit = true;
            private boolean closed;
            private Snapshot snapshot;
        }
    }
}
