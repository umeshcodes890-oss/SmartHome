package com.smarthome.dao;

import com.smarthome.db.Database;
import com.smarthome.model.ActivityLog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class ActivityLogDao {
    public static final int PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    public record Page(List<ActivityLog> entries, boolean hasNext) {
        public Page { entries = List.copyOf(entries); }
    }

    public Page findPage(String search, String eventType, LocalDate from, LocalDate through,
            int offset, int pageSize) throws SQLException {
        int safeOffset = Math.max(0, offset);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        if (from != null && through != null && through.isBefore(from)) {
            throw new IllegalArgumentException("The end date cannot be before the start date.");
        }

        StringBuilder sql = new StringBuilder(
            "SELECT activity_id,event_type,device_id,device_name,schedule_id,occurred_at,outcome,details " +
            "FROM activity_logs WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (device_name LIKE ? OR details LIKE ? OR event_type LIKE ?)");
            String term = "%" + search.trim() + "%";
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
        }
        if (eventType != null && !eventType.isBlank() && !"ALL".equals(eventType)) {
            sql.append(" AND event_type=?");
            parameters.add(eventType);
        }
        if (from != null) {
            sql.append(" AND occurred_at>=?");
            parameters.add(LocalDateTime.ofInstant(from.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneOffset.UTC));
        }
        if (through != null) {
            sql.append(" AND occurred_at<?");
            parameters.add(LocalDateTime.ofInstant(through.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneOffset.UTC));
        }
        sql.append(" ORDER BY occurred_at DESC,activity_id DESC LIMIT ? OFFSET ?");
        parameters.add(safePageSize + 1);
        parameters.add(safeOffset);

        List<ActivityLog> entries = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bind(statement, parameters);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) entries.add(map(results));
            }
        }
        boolean hasNext = entries.size() > safePageSize;
        if (hasNext) entries.remove(entries.size() - 1);
        return new Page(entries, hasNext);
    }

    public List<ActivityLog> findRecent(int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<ActivityLog> entries = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "SELECT activity_id,event_type,device_id,device_name,schedule_id,occurred_at,outcome,details " +
                "FROM activity_logs ORDER BY occurred_at DESC,activity_id DESC LIMIT ?")) {
            statement.setInt(1, safeLimit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) entries.add(map(results));
            }
        }
        return entries;
    }

    public void recordFailure(ActivityLog entry) throws SQLException {
        if (entry.outcome() != ActivityLog.Outcome.FAILED) {
            throw new IllegalArgumentException("Only failed outcomes may be recorded through recordFailure.");
        }
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insert(connection, entry);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    ex.addSuppressed(rollbackFailure);
                }
                throw ex;
            }
        }
    }

    public static void insert(Connection connection, ActivityLog entry) throws SQLException {
        String sql = "INSERT INTO activity_logs " +
            "(event_type,device_id,device_name,schedule_id,occurred_at,outcome,details) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.eventType());
            if (entry.deviceId() == null) statement.setNull(2, java.sql.Types.INTEGER);
            else statement.setLong(2, entry.deviceId());
            statement.setString(3, entry.deviceName());
            if (entry.scheduleId() == null) statement.setNull(4, java.sql.Types.BIGINT);
            else statement.setLong(4, entry.scheduleId());
            statement.setObject(5, LocalDateTime.ofInstant(entry.occurredAt(), ZoneOffset.UTC));
            statement.setString(6, entry.outcome().name());
            statement.setString(7, entry.details());
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object value = parameters.get(index);
            int parameterIndex = index + 1;
            if (value instanceof LocalDateTime dateTime) statement.setObject(parameterIndex, dateTime);
            else if (value instanceof Integer integer) statement.setInt(parameterIndex, integer);
            else statement.setString(parameterIndex, (String) value);
        }
    }

    private static ActivityLog map(ResultSet results) throws SQLException {
        long rawDeviceId = results.getLong("device_id");
        Long deviceId = results.wasNull() ? null : rawDeviceId;
        long rawScheduleId = results.getLong("schedule_id");
        Long scheduleId = results.wasNull() ? null : rawScheduleId;
        LocalDateTime occurredAt = results.getObject("occurred_at", LocalDateTime.class);
        return new ActivityLog(results.getLong("activity_id"), results.getString("event_type"),
            deviceId, results.getString("device_name"), scheduleId,
            occurredAt.toInstant(ZoneOffset.UTC), ActivityLog.Outcome.valueOf(results.getString("outcome")),
            results.getString("details"));
    }
}