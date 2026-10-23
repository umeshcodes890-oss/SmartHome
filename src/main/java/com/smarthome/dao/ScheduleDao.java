package com.smarthome.dao;

import com.smarthome.db.Database;
import com.smarthome.model.ActivityLog;
import com.smarthome.model.DeviceSchedule;
import com.smarthome.model.ScheduleExecution;
import com.smarthome.service.ScheduleRecurrence;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ScheduleDao {
    private final DeviceDao deviceDao;

    public ScheduleDao(DeviceDao deviceDao) {
        this.deviceDao = deviceDao;
    }

    public List<DeviceSchedule> findAll() throws SQLException {
        String sql = "SELECT s.schedule_id,s.device_id,d.device_name,s.schedule_name,s.action,s.repeat_type," +
            "s.created_by_user_id,s.start_date,s.local_time,s.timezone_id,s.weekdays_mask,s.next_run_at,s.enabled " +
            "FROM device_schedules s JOIN devices d ON d.device_id=s.device_id " +
            "ORDER BY s.enabled DESC,s.next_run_at,s.schedule_id DESC";
        List<DeviceSchedule> schedules = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) schedules.add(toSchedule(results));
        }
        return schedules;
    }

    public List<ScheduleExecution> findHistory(int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String sql = "SELECT execution_id,schedule_id,schedule_name,device_name,action,occurrence_at," +
            "executed_at,result,details FROM schedule_execution_log ORDER BY occurrence_at DESC LIMIT ?";
        List<ScheduleExecution> history = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    long rawScheduleId = results.getLong("schedule_id");
                    Long scheduleId = results.wasNull() ? null : rawScheduleId;
                    LocalDateTime occurrence = results.getObject("occurrence_at", LocalDateTime.class);
                    LocalDateTime executed = results.getObject("executed_at", LocalDateTime.class);
                    history.add(new ScheduleExecution(results.getLong("execution_id"), scheduleId,
                        results.getString("schedule_name"), results.getString("device_name"),
                        results.getString("action"), toInstant(occurrence), executed == null ? null : toInstant(executed),
                        results.getString("result"), results.getString("details")));
                }
            }
        }
        return history;
    }

    public long create(int deviceId, String scheduleName, String action, ScheduleRecurrence.Pattern pattern,
            LocalDate startDate, LocalTime localTime, ZoneId zone, Set<DayOfWeek> weekdays, Instant now)
            throws SQLException {
        return create(deviceId, scheduleName, action, pattern, startDate, localTime, zone, weekdays, now, null);
    }

    public long create(int deviceId, String scheduleName, String action, ScheduleRecurrence.Pattern pattern,
            LocalDate startDate, LocalTime localTime, ZoneId zone, Set<DayOfWeek> weekdays, Instant now,
            Long actorUserId) throws SQLException {
        validate(deviceId, scheduleName, action, pattern, startDate, localTime, zone, weekdays);
        Instant next = ScheduleRecurrence.next(startDate, localTime, zone, pattern, weekdays, now.minusNanos(1));
        if (next == null) throw new IllegalArgumentException("This one-time schedule is already in the past.");
        int mask = weekdaysMask(weekdays);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String sql = "INSERT INTO device_schedules " +
                    "(device_id,created_by_user_id,schedule_name,action,repeat_type,start_date,local_time,timezone_id,weekdays_mask,next_run_at,enabled) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,TRUE)";
                long id;
                try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, deviceId);
                    if (actorUserId == null) statement.setNull(2, java.sql.Types.BIGINT);
                    else statement.setLong(2, actorUserId);
                    bindSchedule(statement, 3, scheduleName, action, pattern, startDate, localTime, zone, mask, next);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Schedule was saved but its ID could not be read.");
                        id = keys.getLong(1);
                    }
                }
                DeviceSchedule schedule = findById(connection, id, false);
                logScheduleActivity(connection, "SCHEDULE_CREATED", actorUserId, schedule, now,
                    "Schedule \"" + scheduleName.trim() + "\" created.");
                connection.commit();
                return id;
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public void update(long id, int deviceId, String scheduleName, String action, ScheduleRecurrence.Pattern pattern,
            LocalDate startDate, LocalTime localTime, ZoneId zone, Set<DayOfWeek> weekdays, Instant now)
            throws SQLException {
        update(id, deviceId, scheduleName, action, pattern, startDate, localTime, zone, weekdays, now, null);
    }

    public void update(long id, int deviceId, String scheduleName, String action, ScheduleRecurrence.Pattern pattern,
            LocalDate startDate, LocalTime localTime, ZoneId zone, Set<DayOfWeek> weekdays, Instant now,
            Long actorUserId) throws SQLException {
        validate(deviceId, scheduleName, action, pattern, startDate, localTime, zone, weekdays);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                DeviceSchedule existing = findById(connection, id, true);
                if (existing == null) throw new SQLException("Schedule not found. Refresh and try again.");
                Instant next = ScheduleRecurrence.next(startDate, localTime, zone, pattern, weekdays,
                    now.minusNanos(1));
                if (existing.enabled() && next == null) {
                    throw new IllegalArgumentException("This one-time schedule is already in the past.");
                }
                if (!existing.enabled()) next = null;
                String sql = "UPDATE device_schedules SET device_id=?,schedule_name=?,action=?,repeat_type=?,start_date=?," +
                    "local_time=?,timezone_id=?,weekdays_mask=?,next_run_at=?,enabled=?,updated_at=UTC_TIMESTAMP(6) " +
                    "WHERE schedule_id=?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindUpdatedSchedule(statement, deviceId, scheduleName, action, pattern, startDate, localTime, zone,
                        weekdaysMask(weekdays), next);
                    statement.setBoolean(10, existing.enabled());
                    statement.setLong(11, id);
                    statement.executeUpdate();
                }
                DeviceSchedule updated = findById(connection, id, false);
                logScheduleActivity(connection, "SCHEDULE_UPDATED", actorUserId, updated, now,
                    "Schedule \"" + scheduleName.trim() + "\" updated.");
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public void setEnabled(long id, boolean enabled, Instant now) throws SQLException {
        setEnabled(id, enabled, now, null);
    }

    public void setEnabled(long id, boolean enabled, Instant now, Long actorUserId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                DeviceSchedule schedule = findById(connection, id, true);
                if (schedule == null) throw new SQLException("Schedule not found.");
                Instant next = enabled ? ScheduleRecurrence.next(schedule.startDate(), schedule.localTime(),
                    schedule.timeZone(), schedule.repeatPattern(), schedule.weekdays(), now.minusNanos(1)) : null;
                if (enabled && next == null) throw new SQLException("This one-time schedule has already passed and cannot be re-enabled.");
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE device_schedules SET enabled=?,next_run_at=?,updated_at=UTC_TIMESTAMP(6) WHERE schedule_id=?")) {
                    statement.setBoolean(1, enabled);
                    setUtc(statement, 2, next);
                    statement.setLong(3, id);
                    statement.executeUpdate();
                }
                logScheduleActivity(connection, enabled ? "SCHEDULE_ENABLED" : "SCHEDULE_DISABLED",
                    actorUserId, schedule, now,
                    "Schedule \"" + schedule.scheduleName() + "\" " + (enabled ? "enabled." : "disabled."));
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public void delete(long id) throws SQLException {
        delete(id, null);
    }

    public void delete(long id, Long actorUserId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                DeviceSchedule schedule = findById(connection, id, true);
                if (schedule == null) throw new SQLException("Schedule not found.");
                logScheduleActivity(connection, "SCHEDULE_DELETED", actorUserId, schedule, Instant.now(),
                    "Schedule \"" + schedule.scheduleName() + "\" deleted.");
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM device_schedules WHERE schedule_id=?")) {
                    statement.setLong(1, id);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public void reconcileOnStartup(Instant now) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<DeviceSchedule> schedules = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT s.schedule_id,s.device_id,d.device_name,s.schedule_name,s.action,s.repeat_type,s.created_by_user_id," +
                        "s.start_date,s.local_time,s.timezone_id,s.weekdays_mask,s.next_run_at,s.enabled " +
                        "FROM device_schedules s JOIN devices d ON d.device_id=s.device_id " +
                        "WHERE s.enabled=TRUE FOR UPDATE");
                     ResultSet results = statement.executeQuery()) {
                    while (results.next()) schedules.add(toSchedule(results));
                }
                for (DeviceSchedule schedule : schedules) {
                    if (schedule.repeatPattern() == ScheduleRecurrence.Pattern.ONCE
                            && schedule.nextExecution() != null && !schedule.nextExecution().isAfter(now)) {
                        recordMissed(connection, schedule, schedule.nextExecution(), now);
                        disable(connection, schedule.id());
                    } else {
                        Instant next = ScheduleRecurrence.next(schedule.startDate(), schedule.localTime(),
                            schedule.timeZone(), schedule.repeatPattern(), schedule.weekdays(), now);
                        if (next == null) {
                            disable(connection, schedule.id());
                        } else {
                            updateNext(connection, schedule.id(), next);
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    public int executeDue(Instant now) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT schedule_id FROM device_schedules WHERE enabled=TRUE AND next_run_at<=? ORDER BY next_run_at")) {
            setUtc(statement, 1, now);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) ids.add(results.getLong(1));
            }
        }
        for (long id : ids) executeOccurrence(id, now);
        return ids.size();
    }

    private void executeOccurrence(long id, Instant now) throws SQLException {
        DeviceSchedule schedule;
        Instant occurrence;
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                schedule = findById(connection, id, true);
                if (schedule == null || !schedule.enabled() || schedule.nextExecution() == null
                        || schedule.nextExecution().isAfter(now)) {
                    connection.rollback();
                    return;
                }
                occurrence = schedule.nextExecution();
                if (occurrenceRecorded(connection, schedule.id(), occurrence)) {
                    advanceAfterExecution(connection, schedule, now);
                    connection.commit();
                    return;
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO schedule_execution_log " +
                        "(schedule_id,schedule_name,device_name,action,occurrence_at,result) VALUES(?,?,?,?,?,'FAILED')")) {
                    statement.setLong(1, schedule.id());
                    statement.setString(2, schedule.scheduleName());
                    statement.setString(3, schedule.deviceName());
                    statement.setString(4, schedule.action());
                    setUtc(statement, 5, occurrence);
                    statement.executeUpdate();
                }

                deviceDao.setStatusInTransaction(connection, schedule.deviceId(), schedule.action(),
                    LocalDateTime.ofInstant(now, ZoneOffset.UTC));

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE schedule_execution_log SET result='SUCCESS',executed_at=?,details=? " +
                        "WHERE schedule_id=? AND occurrence_at=?")) {
                    setUtc(statement, 1, now);
                    statement.setString(2, "Device status set to " + schedule.action());
                    statement.setLong(3, schedule.id());
                    setUtc(statement, 4, occurrence);
                    statement.executeUpdate();
                }
                        ActivityLogDao.insert(connection, ActivityLog.success("SCHEDULE_EXECUTED",
                            schedule.createdByUserId(), (long) schedule.deviceId(), schedule.deviceName(), schedule.id(), now,
                    "Schedule \"" + schedule.scheduleName() + "\" set device status to " + schedule.action() + "."));
                advanceAfterExecution(connection, schedule, now);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            recordFailure(id, now, ex);
            return;
        }
    }

    private void recordFailure(long id, Instant now, SQLException failure) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                DeviceSchedule schedule = findById(connection, id, true);
                if (schedule == null || !schedule.enabled() || schedule.nextExecution() == null
                        || schedule.nextExecution().isAfter(now)) {
                    connection.rollback();
                    return;
                }
                Instant occurrence = schedule.nextExecution();
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO schedule_execution_log " +
                        "(schedule_id,schedule_name,device_name,action,occurrence_at,executed_at,result,details) " +
                        "VALUES(?,?,?,?,?,?,'FAILED',?) ON DUPLICATE KEY UPDATE execution_id=execution_id")) {
                    statement.setLong(1, schedule.id());
                    statement.setString(2, schedule.scheduleName());
                    statement.setString(3, schedule.deviceName());
                    statement.setString(4, schedule.action());
                    setUtc(statement, 5, occurrence);
                    setUtc(statement, 6, now);
                    statement.setString(7, safeMessage(failure));
                    statement.executeUpdate();
                }
                ActivityLogDao.insert(connection, new ActivityLog(0, "SCHEDULE_EXECUTED",
                    schedule.createdByUserId(), (long) schedule.deviceId(), schedule.deviceName(), schedule.id(),
                    now, ActivityLog.Outcome.FAILED,
                    "Schedule \"" + schedule.scheduleName() + "\" failed: " + safeMessage(failure)));
                advanceAfterExecution(connection, schedule, now);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    private void advanceAfterExecution(Connection connection, DeviceSchedule schedule, Instant now)
            throws SQLException {
        Instant next = schedule.repeatPattern() == ScheduleRecurrence.Pattern.ONCE ? null
            : ScheduleRecurrence.next(schedule.startDate(), schedule.localTime(), schedule.timeZone(),
                schedule.repeatPattern(), schedule.weekdays(), now);
        if (next == null) disable(connection, schedule.id());
        else updateNext(connection, schedule.id(), next);
    }

    private void recordMissed(Connection connection, DeviceSchedule schedule, Instant occurrence, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schedule_execution_log " +
                "(schedule_id,schedule_name,device_name,action,occurrence_at,executed_at,result,details) " +
                "VALUES(?,?,?,?,?,?,'MISSED','One-time schedule passed while application scheduler was not running') " +
                "ON DUPLICATE KEY UPDATE execution_id=execution_id")) {
            statement.setLong(1, schedule.id());
            statement.setString(2, schedule.scheduleName());
            statement.setString(3, schedule.deviceName());
            statement.setString(4, schedule.action());
            setUtc(statement, 5, occurrence);
            setUtc(statement, 6, now);
            statement.executeUpdate();
        }
        ActivityLogDao.insert(connection, new ActivityLog(0, "SCHEDULE_EXECUTED",
            schedule.createdByUserId(), (long) schedule.deviceId(), schedule.deviceName(), schedule.id(),
            now, ActivityLog.Outcome.MISSED,
            "Schedule \"" + schedule.scheduleName()
                + "\" was missed because the application scheduler was not running."));
    }

    private DeviceSchedule findById(Connection connection, long id, boolean lock) throws SQLException {
        String sql = "SELECT s.schedule_id,s.device_id,d.device_name,s.schedule_name,s.action,s.repeat_type,s.created_by_user_id," +
            "s.start_date,s.local_time,s.timezone_id,s.weekdays_mask,s.next_run_at,s.enabled " +
            "FROM device_schedules s JOIN devices d ON d.device_id=s.device_id WHERE s.schedule_id=?" +
            (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? toSchedule(results) : null;
            }
        }
    }

    private DeviceSchedule toSchedule(ResultSet results) throws SQLException {
        int mask = results.getInt("weekdays_mask");
        Set<DayOfWeek> weekdays = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if ((mask & (1 << (day.getValue() - 1))) != 0) weekdays.add(day);
        }
        LocalDateTime next = results.getObject("next_run_at", LocalDateTime.class);
        long rawCreatorId = results.getLong("created_by_user_id");
        Long creatorId = results.wasNull() ? null : rawCreatorId;
        return new DeviceSchedule(results.getLong("schedule_id"), results.getInt("device_id"), creatorId,
            results.getString("device_name"), results.getString("schedule_name"), results.getString("action"),
            ScheduleRecurrence.Pattern.valueOf(results.getString("repeat_type")),
            results.getObject("start_date", LocalDate.class), results.getObject("local_time", LocalTime.class),
            ZoneId.of(results.getString("timezone_id")), weekdays,
            next == null ? null : toInstant(next), results.getBoolean("enabled"));
    }

    private void bindUpdatedSchedule(PreparedStatement statement, int deviceId, String scheduleName, String action,
            ScheduleRecurrence.Pattern pattern, LocalDate startDate, LocalTime localTime, ZoneId zone,
            int weekdaysMask, Instant next) throws SQLException {
        statement.setInt(1, deviceId);
        statement.setString(2, scheduleName.trim());
        statement.setString(3, action);
        statement.setString(4, pattern.name());
        statement.setObject(5, startDate);
        statement.setObject(6, localTime);
        statement.setString(7, zone.getId());
        statement.setInt(8, weekdaysMask);
        setUtc(statement, 9, next);
    }

    private void bindSchedule(PreparedStatement statement, int startIndex, String scheduleName, String action,
            ScheduleRecurrence.Pattern pattern, LocalDate startDate, LocalTime localTime, ZoneId zone,
            int weekdaysMask, Instant next) throws SQLException {
        statement.setString(startIndex, scheduleName.trim());
        statement.setString(startIndex + 1, action);
        statement.setString(startIndex + 2, pattern.name());
        statement.setObject(startIndex + 3, startDate);
        statement.setObject(startIndex + 4, localTime);
        statement.setString(startIndex + 5, zone.getId());
        statement.setInt(startIndex + 6, weekdaysMask);
        setUtc(statement, startIndex + 7, next);
    }

    private void logScheduleActivity(Connection connection, String eventType, Long actorUserId,
            DeviceSchedule schedule, Instant occurredAt, String details) throws SQLException {
        ActivityLogDao.insert(connection, ActivityLog.success(eventType, actorUserId,
            (long) schedule.deviceId(), schedule.deviceName(), schedule.id(), occurredAt, details));
    }

    private void validate(int deviceId, String scheduleName, String action, ScheduleRecurrence.Pattern pattern,
            LocalDate startDate, LocalTime localTime, ZoneId zone, Set<DayOfWeek> weekdays) {
        if (deviceId <= 0) throw new IllegalArgumentException("Select a device.");
        if (scheduleName == null || scheduleName.isBlank() || scheduleName.trim().length() > 120) {
            throw new IllegalArgumentException("Schedule name is required and must be at most 120 characters.");
        }
        if (!"ON".equals(action) && !"OFF".equals(action)) throw new IllegalArgumentException("Select ON or OFF.");
        if (pattern == null || startDate == null || localTime == null || zone == null) {
            throw new IllegalArgumentException("Complete all schedule fields.");
        }
        if (pattern == ScheduleRecurrence.Pattern.WEEKDAYS && (weekdays == null || weekdays.isEmpty())) {
            throw new IllegalArgumentException("Choose at least one weekday.");
        }
    }

    private int weekdaysMask(Set<DayOfWeek> weekdays) {
        int mask = 0;
        if (weekdays != null) {
            for (DayOfWeek day : weekdays) mask |= 1 << (day.getValue() - 1);
        }
        return mask;
    }

    private boolean exists(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM device_schedules WHERE schedule_id=?")) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) { return results.next(); }
        }
    }

    private boolean occurrenceRecorded(Connection connection, long id, Instant occurrence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM schedule_execution_log WHERE schedule_id=? AND occurrence_at=?")) {
            statement.setLong(1, id);
            setUtc(statement, 2, occurrence);
            try (ResultSet results = statement.executeQuery()) { return results.next(); }
        }
    }

    private void updateNext(Connection connection, long id, Instant next) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE device_schedules SET next_run_at=?,updated_at=UTC_TIMESTAMP(6) WHERE schedule_id=?")) {
            setUtc(statement, 1, next);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private void disable(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE device_schedules SET enabled=FALSE,next_run_at=NULL,updated_at=UTC_TIMESTAMP(6) WHERE schedule_id=?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private void setUtc(PreparedStatement statement, int index, Instant instant) throws SQLException {
        statement.setObject(index, instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime.toInstant(ZoneOffset.UTC);
    }

    private String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
