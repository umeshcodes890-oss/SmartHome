package com.smarthome.dao;

import com.smarthome.db.Database;
import com.smarthome.model.ActivityLog;
import com.smarthome.model.AutomationRule;
import com.smarthome.model.SensorReading;
import com.smarthome.service.AutomationRuleEvaluator;
import com.smarthome.service.AutomationRuleValidator;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class AutomationDao {
    private final DeviceDao deviceDao;

    public AutomationDao(DeviceDao deviceDao) {
        this.deviceDao = java.util.Objects.requireNonNull(deviceDao);
    }

    public List<AutomationRule> findAll() throws SQLException {
        List<AutomationRule> rules = new ArrayList<>();
        String sql = selectRule() + " ORDER BY r.enabled DESC,r.rule_name";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) rules.add(map(results));
        }
        return List.copyOf(rules);
    }

    public List<AutomationEvaluation> findRecentEvaluations(int limit) throws SQLException {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<AutomationEvaluation> evaluations = new ArrayList<>();
        String sql = "SELECT e.evaluation_id,e.rule_id,e.rule_name,e.condition_met,e.action_executed," +
            "e.outcome,e.evaluated_at,e.details FROM automation_rule_evaluations e " +
            "ORDER BY e.evaluated_at DESC,e.evaluation_id DESC LIMIT ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    LocalDateTime time = results.getObject("evaluated_at", LocalDateTime.class);
                    long rawRuleId = results.getLong("rule_id");
                    Long ruleId = results.wasNull() ? null : rawRuleId;
                    evaluations.add(new AutomationEvaluation(results.getLong("evaluation_id"), ruleId,
                        results.getString("rule_name"), results.getBoolean("condition_met"),
                        results.getBoolean("action_executed"), results.getString("outcome"),
                        time.toInstant(ZoneOffset.UTC), results.getString("details")));
                }
            }
        }
        return List.copyOf(evaluations);
    }

    public long create(String name, boolean enabled, AutomationRule.Condition condition, BigDecimal threshold,
            Integer sensorRoomId, int targetDeviceId, String action, LocalTime scheduledTime,
            ZoneId timeZone, int cooldownSeconds, Long creatorUserId) throws SQLException {
        validate(name, condition, threshold, sensorRoomId, targetDeviceId, action, scheduledTime,
            timeZone, cooldownSeconds);
        validateTarget(condition, targetDeviceId, action);
        String sql = "INSERT INTO automation_rules(rule_name,enabled,condition_type,threshold_value,sensor_room_id," +
            "target_device_id,action,scheduled_time,timezone_id,cooldown_seconds,created_by_user_id) " +
            "VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            bind(statement, name, enabled, condition, threshold, sensorRoomId, targetDeviceId,
                action, scheduledTime, timeZone, cooldownSeconds, creatorUserId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Automation rule was saved but its ID could not be retrieved.");
                return keys.getLong(1);
            }
        }
    }

    public void update(long id, String name, boolean enabled, AutomationRule.Condition condition,
            BigDecimal threshold, Integer sensorRoomId, int targetDeviceId, String action,
            LocalTime scheduledTime, ZoneId timeZone, int cooldownSeconds) throws SQLException {
        validate(name, condition, threshold, sensorRoomId, targetDeviceId, action, scheduledTime,
            timeZone, cooldownSeconds);
        validateTarget(condition, targetDeviceId, action);
        String sql = "UPDATE automation_rules SET rule_name=?,enabled=?,condition_type=?,threshold_value=?," +
            "sensor_room_id=?,target_device_id=?,action=?,scheduled_time=?,timezone_id=?,cooldown_seconds=?," +
            "last_condition_met=FALSE,last_scheduled_date=NULL " +
            "WHERE rule_id=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name.trim());
            statement.setBoolean(2, enabled);
            statement.setString(3, condition.name());
            setNullableDecimal(statement, 4, threshold);
            setNullableInteger(statement, 5, sensorRoomId);
            statement.setInt(6, targetDeviceId);
            statement.setString(7, action);
            if (scheduledTime == null) statement.setNull(8, java.sql.Types.TIME);
            else statement.setObject(8, scheduledTime);
            statement.setString(9, timeZone.getId());
            statement.setInt(10, cooldownSeconds);
            statement.setLong(11, id);
            if (statement.executeUpdate() != 1) throw new SQLException("Automation rule not found.");
        }
    }

    public void setEnabled(long id, boolean enabled) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE automation_rules SET enabled=? WHERE rule_id=?")) {
            statement.setBoolean(1, enabled);
            statement.setLong(2, id);
            if (statement.executeUpdate() != 1) throw new SQLException("Automation rule not found.");
        }
    }

    public void delete(long id) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM automation_rules WHERE rule_id=?")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() != 1) throw new SQLException("Automation rule not found.");
        }
    }

    public int evaluateEnabled(Instant now, boolean simulatedReadingsEnabled) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT rule_id FROM automation_rules WHERE enabled=TRUE ORDER BY rule_id");
             ResultSet results = statement.executeQuery()) {
            while (results.next()) ids.add(results.getLong(1));
        }
        int evaluated = 0;
        for (long id : ids) {
            if (evaluateOne(id, now, simulatedReadingsEnabled)) evaluated++;
        }
        return evaluated;
    }

    private boolean evaluateOne(long id, Instant now, boolean simulatedReadingsEnabled) throws SQLException {
        AutomationRule rule = null;
        SensorReading reading = null;
        AutomationRuleEvaluator.Decision decision;
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                rule = findById(connection, id, true);
                if (rule == null || !rule.enabled()) {
                    connection.commit();
                    return false;
                }
                reading = rule.condition().usesSensor() ? findLatestSensor(connection, rule) : null;
                if (reading != null && reading.recordedAt().isBefore(now.minusSeconds(120))) reading = null;
                if (reading != null && reading.simulated() && !simulatedReadingsEnabled) reading = null;
                decision = AutomationRuleEvaluator.evaluate(rule, reading, now);
                boolean changed = rule.condition() == AutomationRule.Condition.SCHEDULED_TIME
                    ? decision.scheduledDate() != null
                    : decision.conditionMet() != rule.lastConditionMet();
                if (!changed) {
                    connection.commit();
                    return false;
                }
                String outcome = decision.cooldownSuppressed() ? "SUPPRESSED" : "SUCCESS";
                String details = decision.explanation();
                if (decision.trigger()) {
                    try {
                        deviceDao.setStatusInTransaction(connection, rule.targetDeviceId(), rule.action(),
                            LocalDateTime.ofInstant(now, ZoneOffset.UTC));
                        ActivityLogDao.insert(connection, ActivityLog.success("AUTOMATION_EXECUTED",
                            (Long) null, (long) rule.targetDeviceId(), rule.targetDeviceName(), null, now,
                            "Rule \"" + rule.name() + "\" set device status to " + rule.action() + "."));
                    } catch (SQLException | RuntimeException actionFailure) {
                        throw new RuleActionException(actionFailure);
                    }
                    details += " Target " + rule.targetDeviceName() + " set to " + rule.action() + ".";
                }
                updateEvaluationState(connection, rule, decision, decision.trigger() ? now : null);
                insertEvaluation(connection, rule, decision, outcome, decision.trigger(), now, details);
                connection.commit();
                return true;
            } catch (RuleActionException ex) {
                rollback(connection, ex);
                if (rule != null) {
                    AutomationRuleEvaluator.Decision failedDecision =
                        AutomationRuleEvaluator.evaluate(rule, reading, now);
                    try {
                        recordFailedEvaluation(rule, failedDecision, now, ex.getCause());
                    } catch (SQLException loggingFailure) {
                        ex.addSuppressed(loggingFailure);
                    }
                }
                throw new SQLException("Automation rule could not control its target device.",
                    ex.getCause());
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    private void recordFailedEvaluation(AutomationRule rule, AutomationRuleEvaluator.Decision decision,
            Instant now, Throwable failure) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                updateEvaluationState(connection, rule, decision, null);
                String details = "Rule action failed: " + safeMessage(failure);
                insertEvaluation(connection, rule, decision, "FAILED", false, now, details);
                ActivityLogDao.insert(connection, ActivityLog.failure("AUTOMATION_EXECUTED",
                    (long) rule.targetDeviceId(), rule.targetDeviceName(), null, now,
                    "Rule \"" + rule.name() + "\" failed: " + safeMessage(failure)));
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(connection, ex);
                throw ex;
            }
        }
    }

    private void updateEvaluationState(Connection connection, AutomationRule rule,
            AutomationRuleEvaluator.Decision decision, Instant triggeredAt) throws SQLException {
        String sql = "UPDATE automation_rules SET last_condition_met=?,last_triggered_at=COALESCE(?,last_triggered_at)," +
            "last_scheduled_date=COALESCE(?,last_scheduled_date) WHERE rule_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            boolean conditionMet = rule.condition() == AutomationRule.Condition.SCHEDULED_TIME
                ? false : decision.conditionMet();
            statement.setBoolean(1, conditionMet);
            setUtc(statement, 2, triggeredAt);
            if (decision.scheduledDate() == null) statement.setNull(3, java.sql.Types.DATE);
            else statement.setObject(3, decision.scheduledDate());
            statement.setLong(4, rule.id());
            statement.executeUpdate();
        }
    }

    private void insertEvaluation(Connection connection, AutomationRule rule,
            AutomationRuleEvaluator.Decision decision, String outcome, boolean actionExecuted,
            Instant now, String details) throws SQLException {
        String sql = "INSERT INTO automation_rule_evaluations(rule_id,rule_name,condition_met,action_executed," +
            "outcome,evaluated_at,details) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, rule.id());
            statement.setString(2, rule.name());
            statement.setBoolean(3, decision.conditionMet());
            statement.setBoolean(4, actionExecuted);
            statement.setString(5, outcome);
            setUtc(statement, 6, now);
            statement.setString(7, details);
            statement.executeUpdate();
        }
    }

    private SensorReading findLatestSensor(Connection connection, AutomationRule rule) throws SQLException {
        SensorReading.Type type = switch (rule.condition()) {
            case TEMPERATURE_ABOVE -> SensorReading.Type.TEMPERATURE;
            case HUMIDITY_ABOVE -> SensorReading.Type.HUMIDITY;
            case MOTION_DETECTED -> SensorReading.Type.MOTION;
            case SCHEDULED_TIME -> throw new IllegalArgumentException("Scheduled rules do not read sensors.");
        };
        String sql = "SELECT s.reading_id,s.room_id,r.room_name,s.sensor_type,s.reading_value,s.simulated,s.recorded_at " +
            "FROM sensor_readings s JOIN rooms r ON r.room_id=s.room_id WHERE s.room_id=? AND s.sensor_type=? " +
            "ORDER BY s.recorded_at DESC,s.reading_id DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, rule.sensorRoomId());
            statement.setString(2, type.name());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? SensorDao.map(results, results.getString("room_name")) : null;
            }
        }
    }

    private AutomationRule findById(Connection connection, long id, boolean lock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                selectRule() + " WHERE r.rule_id=?" + (lock ? " FOR UPDATE" : ""))) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? map(results) : null;
            }
        }
    }

    private static String selectRule() {
        return "SELECT r.rule_id,r.rule_name,r.enabled,r.condition_type,r.threshold_value,r.sensor_room_id," +
            "sr.room_name AS sensor_room_name,r.target_device_id,d.device_name AS target_device_name," +
            "d.device_type AS target_device_type,r.action,r.scheduled_time,r.timezone_id,r.cooldown_seconds," +
            "r.last_condition_met,r.last_triggered_at,r.last_scheduled_date FROM automation_rules r " +
            "LEFT JOIN rooms sr ON sr.room_id=r.sensor_room_id JOIN devices d ON d.device_id=r.target_device_id";
    }

    private static AutomationRule map(ResultSet results) throws SQLException {
        int rawRoomId = results.getInt("sensor_room_id");
        Integer roomId = results.wasNull() ? null : rawRoomId;
        LocalDateTime triggeredAt = results.getObject("last_triggered_at", LocalDateTime.class);
        LocalTime scheduledTime = results.getObject("scheduled_time", LocalTime.class);
        return new AutomationRule(results.getLong("rule_id"), results.getString("rule_name"),
            results.getBoolean("enabled"),
            AutomationRule.Condition.valueOf(results.getString("condition_type")),
            results.getBigDecimal("threshold_value"), roomId, results.getString("sensor_room_name"),
            results.getInt("target_device_id"), results.getString("target_device_name"),
            results.getString("target_device_type"), results.getString("action"), scheduledTime,
            ZoneId.of(results.getString("timezone_id")), results.getInt("cooldown_seconds"),
            results.getBoolean("last_condition_met"),
            triggeredAt == null ? null : triggeredAt.toInstant(ZoneOffset.UTC),
            results.getObject("last_scheduled_date", LocalDate.class));
    }

    private void validateTarget(AutomationRule.Condition condition, int deviceId, String action)
            throws SQLException {
        String sql = "SELECT device_type FROM devices WHERE device_id=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, deviceId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) throw new IllegalArgumentException("Select an existing target device.");
                AutomationRuleValidator.validateTargetType(condition, results.getString(1), action);
            }
        }
    }

    private static void validate(String name, AutomationRule.Condition condition, BigDecimal threshold,
            Integer roomId, int targetDeviceId, String action, LocalTime scheduledTime, ZoneId timeZone,
            int cooldownSeconds) {
        AutomationRuleValidator.validate(name, condition, threshold, roomId, targetDeviceId,
            action, scheduledTime, timeZone, cooldownSeconds);
    }

    private static void bind(PreparedStatement statement, String name, boolean enabled,
            AutomationRule.Condition condition, BigDecimal threshold, Integer sensorRoomId,
            int targetDeviceId, String action, LocalTime scheduledTime, ZoneId timeZone,
            int cooldownSeconds, Long creatorUserId) throws SQLException {
        statement.setString(1, name.trim());
        statement.setBoolean(2, enabled);
        statement.setString(3, condition.name());
        setNullableDecimal(statement, 4, threshold);
        setNullableInteger(statement, 5, sensorRoomId);
        statement.setInt(6, targetDeviceId);
        statement.setString(7, action);
        if (scheduledTime == null) statement.setNull(8, java.sql.Types.TIME);
        else statement.setObject(8, scheduledTime);
        statement.setString(9, timeZone.getId());
        statement.setInt(10, cooldownSeconds);
        if (creatorUserId == null) statement.setNull(11, java.sql.Types.BIGINT);
        else statement.setLong(11, creatorUserId);
    }

    private static void setNullableDecimal(PreparedStatement statement, int index, BigDecimal value)
            throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.DECIMAL);
        else statement.setBigDecimal(index, value);
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.INTEGER);
        else statement.setInt(index, value);
    }

    private static void setUtc(PreparedStatement statement, int index, Instant instant) throws SQLException {
        if (instant == null) statement.setNull(index, java.sql.Types.TIMESTAMP);
        else statement.setObject(index, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private static void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank() ? "Unexpected error" : message;
    }

    private static final class RuleActionException extends Exception {
        private static final long serialVersionUID = 1L;
        RuleActionException(Throwable cause) { super(cause); }
    }

    public record AutomationEvaluation(long id, Long ruleId, String ruleName, boolean conditionMet,
            boolean actionExecuted, String outcome, Instant evaluatedAt, String details) {}
}
