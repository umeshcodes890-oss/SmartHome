package com.smarthome.dao;

import com.smarthome.db.Database;
import com.smarthome.model.ActivityLog;
import com.smarthome.model.Device;
import com.smarthome.model.RoomSummary;
import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class DeviceDao {
    public List<Device> findAll() throws SQLException {
        String sql = "SELECT d.device_id,d.device_name,d.device_type,r.room_name,d.status,d.power_watts " +
                     "FROM devices d JOIN rooms r ON r.room_id=d.room_id ORDER BY d.device_id DESC";
        List<Device> result = new ArrayList<>();
        try (Connection c=Database.getConnection(); PreparedStatement ps=c.prepareStatement(sql); ResultSet rs=ps.executeQuery()) {
            while(rs.next()) result.add(new Device(rs.getInt(1),rs.getString(2),rs.getString(3),
                rs.getString(4),rs.getString(5),rs.getBigDecimal(6)));
        }
        return result;
    }

    public List<Device> findByRoomId(int roomId) throws SQLException {
        String sql = "SELECT d.device_id,d.device_name,d.device_type,r.room_name,d.status,d.power_watts " +
            "FROM devices d JOIN rooms r ON r.room_id=d.room_id WHERE r.room_id=? ORDER BY d.device_name";
        List<Device> result = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, roomId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    result.add(new Device(results.getInt(1), results.getString(2), results.getString(3),
                        results.getString(4), results.getString(5), results.getBigDecimal(6)));
                }
            }
        }
        return result;
    }

    public void setStatus(int id, String status) throws SQLException {
        if (!"ON".equals(status) && !"OFF".equals(status)) throw new IllegalArgumentException("Invalid status");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                setStatusInTransaction(c, id, status, utcNow());
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            }
        }
    }

    public void setStatusInTransaction(Connection connection, int id, String status, LocalDateTime changedAt)
            throws SQLException {
        if (!"ON".equals(status) && !"OFF".equals(status)) throw new IllegalArgumentException("Invalid status");
        DeviceSnapshot current = findSnapshot(connection, id, true);
        if (current == null) throw new SQLException("Scheduled device no longer exists.");
        if (current.status().equals(status)) {
            logActivity(connection, "DEVICE_STATUS", current, null, utcNow(),
                "ON/OFF action completed; device was already " + status + ".");
            return;
        }
        if ("ON".equals(current.status())) closeUsageSession(connection, id, changedAt);
        updateStatus(connection, id, status);
        DeviceSnapshot updated = new DeviceSnapshot(current.id(), current.name(), current.type(),
            current.roomId(), current.roomName(), status, current.watts());
        recordStatus(connection, updated, changedAt);
        if ("ON".equals(status)) openUsageSession(connection, updated, changedAt);
        logActivity(connection, "DEVICE_STATUS", updated, null, changedAt,
            "Status changed from " + current.status() + " to " + status + ".");
    }

    public void add(String name, String type, int roomId, String status, BigDecimal watts) throws SQLException {
        validateDevice(name, type, status, watts);
        String sql = "INSERT INTO devices(device_name,device_type,room_id,status,power_watts) VALUES(?,?,?,?,?)";
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
            lockDeviceNames(c);
            ensureUniqueName(c, name, -1);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, name.trim());
                ps.setString(2, type.trim());
                ps.setInt(3, roomId);
                ps.setString(4, status);
                ps.setBigDecimal(5, watts);
                ps.executeUpdate();
            }
            int id = lastInsertedDeviceId(c);
            DeviceSnapshot created = findSnapshot(c, id, false);
            if ("ON".equals(status)) {
                LocalDateTime startedAt = utcNow();
                recordStatus(c, created, startedAt);
                openUsageSession(c, created, startedAt);
            }
            logActivity(c, "DEVICE_CREATED", created, null, utcNow(),
                "Device created as " + created.status() + " in " + created.roomName()
                    + " with rated power " + created.watts().toPlainString() + " W.");
            c.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            }
        }
    }

    public void update(int id, String name, String type, int roomId, BigDecimal watts) throws SQLException {
        validateDevice(name, type, "OFF", watts);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
            lockDeviceNames(c);
            ensureUniqueName(c, name, id);
            DeviceSnapshot current = findSnapshot(c, id, true);
            if (current == null) throw new SQLException("Device not found.");
            LocalDateTime changedAt = utcNow();
            if ("ON".equals(current.status())) closeUsageSession(c, id, changedAt);
            String sql = "UPDATE devices SET device_name=?,device_type=?,room_id=?,power_watts=? WHERE device_id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, name.trim());
                ps.setString(2, type.trim());
                ps.setInt(3, roomId);
                ps.setBigDecimal(4, watts);
                ps.setInt(5, id);
                if (ps.executeUpdate() != 1 && !exists(c, id)) throw new SQLException("Device not found.");
            }
            if ("ON".equals(current.status())) {
                DeviceSnapshot updated = findSnapshot(c, id, false);
                openUsageSession(c, updated, changedAt);
            }
            DeviceSnapshot updated = findSnapshot(c, id, false);
            logActivity(c, "DEVICE_UPDATED", updated, null, changedAt,
                "Device details updated: name " + current.name() + " → " + updated.name()
                    + ", type " + current.type() + " → " + updated.type()
                    + ", room " + current.roomName() + " → " + updated.roomName()
                    + ", rated power " + current.watts().toPlainString() + " W → "
                    + updated.watts().toPlainString() + " W.");
            c.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            }
        }
    }

    public int setAllStatus(String status) throws SQLException {
        if (!"ON".equals(status) && !"OFF".equals(status)) throw new IllegalArgumentException("Invalid status");
        try (Connection c = Database.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                List<DeviceSnapshot> changes = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT d.device_id,d.device_name,d.device_type,d.room_id,r.room_name,d.status,d.power_watts " +
                        "FROM devices d JOIN rooms r ON r.room_id=d.room_id WHERE d.status<>? FOR UPDATE")) {
                    ps.setString(1, status);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) changes.add(snapshot(rs));
                    }
                }
                LocalDateTime changedAt = utcNow();
                for (DeviceSnapshot current : changes) {
                    if ("ON".equals(current.status())) closeUsageSession(c, current.id(), changedAt);
                    updateStatus(c, current.id(), status);
                    DeviceSnapshot updated = new DeviceSnapshot(current.id(), current.name(), current.type(),
                        current.roomId(), current.roomName(), status, current.watts());
                    recordStatus(c, updated, changedAt);
                    if ("ON".equals(status)) openUsageSession(c, updated, changedAt);
                    logActivity(c, "DEVICE_STATUS", updated, null, changedAt,
                        "Bulk action changed status from " + current.status() + " to " + status + ".");
                }
                c.commit();
                return changes.size();
            } catch (SQLException ex) {
                try {
                    c.rollback();
                } catch (SQLException rollbackError) {
                    ex.addSuppressed(rollbackError);
                }
                throw ex;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }

    private void ensureUniqueName(Connection c, String name, int excludedId) throws SQLException {
        String sql = "SELECT device_id FROM devices WHERE LOWER(device_name)=LOWER(?) AND device_id<>? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setInt(2, excludedId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new SQLException("Another device already uses the name \"" + name.trim() + "\".");
                }
            }
        }
    }

    private int lastInsertedDeviceId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT LAST_INSERT_ID()");
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) throw new SQLException("Could not retrieve the new device ID.");
            return results.getInt(1);
        }
    }

    private void logActivity(Connection connection, String eventType, DeviceSnapshot device,
            Long scheduleId, LocalDateTime occurredAt, String details) throws SQLException {
        ActivityLogDao.insert(connection, ActivityLog.success(eventType, (long) device.id(), device.name(),
            scheduleId, occurredAt.toInstant(ZoneOffset.UTC), details));
    }

    private void lockDeviceNames(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, "com.smarthome.devices.name");
            ps.setInt(2, 10);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new SQLException("Could not safely validate device-name uniqueness. Please retry.");
                }
            }
        }
    }

    private boolean exists(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM devices WHERE device_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void validateDevice(String name, String type, String status, BigDecimal watts) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new IllegalArgumentException("Device name is required and must be at most 100 characters.");
        }
        if (type == null || type.isBlank() || type.trim().length() > 40) {
            throw new IllegalArgumentException("Device type is required and must be at most 40 characters.");
        }
        if (!"ON".equals(status) && !"OFF".equals(status)) {
            throw new IllegalArgumentException("Invalid device status.");
        }
        if (watts == null || watts.signum() < 0 || watts.scale() > 2 || watts.precision() > 8) {
            throw new IllegalArgumentException("Power must be between 0 and 999999.99 watts, with at most two decimal places.");
        }
    }

    private DeviceSnapshot findSnapshot(Connection connection, int id, boolean lock) throws SQLException {
        String sql = "SELECT d.device_id,d.device_name,d.device_type,d.room_id,r.room_name,d.status,d.power_watts " +
            "FROM devices d JOIN rooms r ON r.room_id=d.room_id WHERE d.device_id=?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? snapshot(results) : null;
            }
        }
    }

    private DeviceSnapshot snapshot(ResultSet results) throws SQLException {
        return new DeviceSnapshot(results.getInt(1), results.getString(2), results.getString(3),
            results.getInt(4), results.getString(5), results.getString(6), results.getBigDecimal(7));
    }

    private void updateStatus(Connection connection, int id, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE devices SET status=? WHERE device_id=?")) {
            statement.setString(1, status);
            statement.setInt(2, id);
            if (statement.executeUpdate() != 1) throw new SQLException("Device not found.");
        }
    }

    private void closeUsageSession(Connection connection, int deviceId, LocalDateTime endedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE energy_usage_sessions SET ended_at=? WHERE device_id=? AND ended_at IS NULL")) {
            statement.setObject(1, endedAt);
            statement.setInt(2, deviceId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("No open energy usage session exists for this active device. Apply the energy-history migration and refresh.");
            }
        }
    }

    private void openUsageSession(Connection connection, DeviceSnapshot device, LocalDateTime startedAt) throws SQLException {
        String sql = "INSERT INTO energy_usage_sessions " +
            "(device_id,device_name,device_type,room_id,room_name,power_watts,started_at) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, device.id());
            statement.setString(2, device.name());
            statement.setString(3, device.type());
            statement.setInt(4, device.roomId());
            statement.setString(5, device.roomName());
            statement.setBigDecimal(6, device.watts());
            statement.setObject(7, startedAt);
            statement.executeUpdate();
        }
    }

    private void recordStatus(Connection connection, DeviceSnapshot device, LocalDateTime changedAt) throws SQLException {
        String sql = "INSERT INTO device_status_history " +
            "(device_id,device_name,device_type,room_id,room_name,status,power_watts,changed_at) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, device.id());
            statement.setString(2, device.name());
            statement.setString(3, device.type());
            statement.setInt(4, device.roomId());
            statement.setString(5, device.roomName());
            statement.setString(6, device.status());
            statement.setBigDecimal(7, device.watts());
            statement.setObject(8, changedAt);
            statement.executeUpdate();
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private record DeviceSnapshot(int id, String name, String type, int roomId, String roomName,
            String status, BigDecimal watts) {}

    public List<String> findRoomNames() throws SQLException {
        List<String> rooms = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT room_name FROM rooms ORDER BY room_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rooms.add(rs.getString(1));
        }
        return rooms;
    }

    public void delete(int id) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                DeviceSnapshot current = findSnapshot(c, id, true);
                if (current == null) throw new SQLException("Device not found.");
                if ("ON".equals(current.status())) {
                    LocalDateTime changedAt = utcNow();
                    closeUsageSession(c, id, changedAt);
                    DeviceSnapshot off = new DeviceSnapshot(current.id(), current.name(), current.type(),
                        current.roomId(), current.roomName(), "OFF", current.watts());
                    recordStatus(c, off, changedAt);
                }
                logActivity(c, "DEVICE_DELETED", current, null, utcNow(),
                    "Device deleted from " + current.roomName() + " with status " + current.status() + ".");
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM devices WHERE device_id=?")) {
                    ps.setInt(1, id);
                    if (ps.executeUpdate() != 1) throw new SQLException("Device not found.");
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(c, ex);
                throw ex;
            }
        }
    }

    public Map<String,Integer> getRoomIds() throws SQLException {
        Map<String,Integer> rooms=new LinkedHashMap<>();
        try(Connection c=Database.getConnection(); PreparedStatement ps=c.prepareStatement("SELECT room_id,room_name FROM rooms ORDER BY room_name");
            ResultSet rs=ps.executeQuery()) {
            while(rs.next()) rooms.put(rs.getString("room_name"),rs.getInt("room_id"));
        }
        return rooms;
    }

    public List<RoomSummary> findRoomSummaries() throws SQLException {
        String sql = "SELECT r.room_id, r.room_name, COUNT(d.device_id) AS device_count, " +
                     "COALESCE(SUM(CASE WHEN d.status='ON' THEN 1 ELSE 0 END), 0) AS active_count " +
                     "FROM rooms r LEFT JOIN devices d ON d.room_id=r.room_id " +
                     "GROUP BY r.room_id, r.room_name ORDER BY r.room_name";
        List<RoomSummary> result = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new RoomSummary(rs.getInt("room_id"), rs.getString("room_name"),
                    rs.getInt("device_count"), rs.getInt("active_count")));
            }
        }
        return result;
    }
}