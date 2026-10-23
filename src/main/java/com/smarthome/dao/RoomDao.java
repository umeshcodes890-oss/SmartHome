package com.smarthome.dao;

import com.smarthome.db.Database;
import com.smarthome.model.Room;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class RoomDao {
    public List<Room> findAll() throws SQLException {
        String sql = "SELECT r.room_id, r.room_name, r.floor_no, COUNT(d.device_id) AS device_count, " +
            "COALESCE(SUM(CASE WHEN d.status='ON' THEN 1 ELSE 0 END), 0) AS active_count " +
            "FROM rooms r LEFT JOIN devices d ON d.room_id=r.room_id " +
            "GROUP BY r.room_id, r.room_name, r.floor_no ORDER BY r.floor_no, r.room_name";
        List<Room> rooms = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                rooms.add(new Room(results.getInt("room_id"), results.getString("room_name"),
                    results.getInt("floor_no"), results.getInt("device_count"), results.getInt("active_count")));
            }
        }
        return rooms;
    }

    public void add(String name, int floorNumber) throws SQLException {
        validate(name, floorNumber);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockRoomNames(connection);
                ensureUniqueName(connection, name, -1);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO rooms(room_name, floor_no) VALUES(?, ?)")) {
                    statement.setString(1, name.trim());
                    statement.setInt(2, floorNumber);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public void update(int id, String name, int floorNumber) throws SQLException {
        validate(name, floorNumber);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockRoomNames(connection);
                ensureUniqueName(connection, name, id);
                String oldName = findRoomName(connection, id);
                if (oldName == null) throw new SQLException("Room not found. Refresh the room list and try again.");
                List<ActiveDevice> activeDevices = oldName.equals(name.trim())
                    ? List.of() : findActiveDevices(connection, id);
                LocalDateTime changedAt = LocalDateTime.now(ZoneOffset.UTC);
                for (ActiveDevice device : activeDevices) closeUsageSession(connection, device.id(), changedAt);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE rooms SET room_name=?, floor_no=? WHERE room_id=?")) {
                    statement.setString(1, name.trim());
                    statement.setInt(2, floorNumber);
                    statement.setInt(3, id);
                    if (statement.executeUpdate() != 1 && !roomExists(connection, id)) {
                        throw new SQLException("Room not found. Refresh the room list and try again.");
                    }
                }
                for (ActiveDevice device : activeDevices) {
                    openUsageSession(connection, device, id, name.trim(), changedAt);
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int assignedDevices = countDevices(connection, id);
                if (assignedDevices > 0) {
                    throw new SQLException("This room has " + assignedDevices +
                        " assigned device(s). Move or delete those devices before deleting the room.");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM rooms WHERE room_id=?")) {
                    statement.setInt(1, id);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Room not found. Refresh the room list and try again.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                if (ex instanceof SQLException sqlException
                        && (sqlException instanceof SQLIntegrityConstraintViolationException
                        || sqlException.getSQLState() != null && sqlException.getSQLState().startsWith("23"))) {
                    throw new SQLException("This room has assigned devices. Move or delete those devices before deleting the room.", ex);
                }
                throw ex;
            }
        }
    }

    private void validate(String name, int floorNumber) {
        if (name == null || name.isBlank() || name.trim().length() > 80) {
            throw new IllegalArgumentException("Room name is required and must be at most 80 characters.");
        }
        if (floorNumber < 0 || floorNumber > 999) {
            throw new IllegalArgumentException("Floor number must be between 0 and 999.");
        }
    }

    private void ensureUniqueName(Connection connection, String name, int excludedId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT room_id FROM rooms WHERE LOWER(room_name)=LOWER(?) AND room_id<>? LIMIT 1")) {
            statement.setString(1, name.trim());
            statement.setInt(2, excludedId);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    throw new SQLException("A room named \"" + name.trim() + "\" already exists.");
                }
            }
        }
    }

    private void lockRoomNames(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, "com.smarthome.rooms.name");
            statement.setInt(2, 10);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next() || results.getInt(1) != 1) {
                    throw new SQLException("Could not safely validate room-name uniqueness. Please retry.");
                }
            }
        }
    }

    private boolean roomExists(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM rooms WHERE room_id=?")) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private String findRoomName(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT room_name FROM rooms WHERE room_id=?")) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getString(1) : null;
            }
        }
    }

    private List<ActiveDevice> findActiveDevices(Connection connection, int roomId) throws SQLException {
        List<ActiveDevice> devices = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT device_id,device_name,device_type,power_watts FROM devices WHERE room_id=? AND status='ON' FOR UPDATE")) {
            statement.setInt(1, roomId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    devices.add(new ActiveDevice(results.getInt(1), results.getString(2),
                        results.getString(3), results.getBigDecimal(4)));
                }
            }
        }
        return devices;
    }

    private void closeUsageSession(Connection connection, int deviceId, LocalDateTime endedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE energy_usage_sessions SET ended_at=? WHERE device_id=? AND ended_at IS NULL")) {
            statement.setObject(1, endedAt);
            statement.setInt(2, deviceId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("An active device has no open energy session. Apply the energy-history migration and refresh.");
            }
        }
    }

    private void openUsageSession(Connection connection, ActiveDevice device, int roomId, String roomName,
            LocalDateTime startedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO energy_usage_sessions " +
                "(device_id,device_name,device_type,room_id,room_name,power_watts,started_at) VALUES(?,?,?,?,?,?,?)")) {
            statement.setInt(1, device.id());
            statement.setString(2, device.name());
            statement.setString(3, device.type());
            statement.setInt(4, roomId);
            statement.setString(5, roomName);
            statement.setBigDecimal(6, device.watts());
            statement.setObject(7, startedAt);
            statement.executeUpdate();
        }
    }

    private int countDevices(Connection connection, int roomId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM devices WHERE room_id=?")) {
            statement.setInt(1, roomId);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getInt(1);
            }
        }
    }

    private void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private record ActiveDevice(int id, String name, String type, java.math.BigDecimal watts) {}
}
