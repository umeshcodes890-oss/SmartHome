package com.smarthome.service;

import com.smarthome.model.AutomationRule;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;

public final class AutomationRuleValidator {
    private AutomationRuleValidator() {}

    public static void validate(String name, AutomationRule.Condition condition, BigDecimal threshold,
            Integer sensorRoomId, int targetDeviceId, String action, LocalTime scheduledTime,
            ZoneId timeZone, int cooldownSeconds) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new IllegalArgumentException("Rule name is required and must be at most 100 characters.");
        }
        if (condition == null) throw new IllegalArgumentException("Choose a rule condition.");
        if (targetDeviceId <= 0) throw new IllegalArgumentException("Choose a target device.");
        if (!"ON".equals(action) && !"OFF".equals(action)) throw new IllegalArgumentException("Choose ON or OFF action.");
        if (timeZone == null) throw new IllegalArgumentException("Choose a valid timezone.");
        if (cooldownSeconds < 0 || cooldownSeconds > 86_400) {
            throw new IllegalArgumentException("Cooldown must be between 0 and 86400 seconds.");
        }
        if (condition.usesSensor() && (sensorRoomId == null || sensorRoomId <= 0)) {
            throw new IllegalArgumentException("Choose the room whose sensor should be evaluated.");
        }
        if (!condition.usesSensor() && sensorRoomId != null) {
            throw new IllegalArgumentException("Scheduled-time rules do not use a sensor room.");
        }
        if (condition == AutomationRule.Condition.TEMPERATURE_ABOVE
                && (threshold == null || threshold.compareTo(BigDecimal.valueOf(-20)) < 0
                    || threshold.compareTo(BigDecimal.valueOf(60)) > 0 || threshold.scale() > 2)) {
            throw new IllegalArgumentException("Temperature threshold must be between -20 and 60 °C with at most two decimals.");
        }
        if (condition == AutomationRule.Condition.HUMIDITY_ABOVE
                && (threshold == null || threshold.signum() < 0
                    || threshold.compareTo(BigDecimal.valueOf(100)) > 0 || threshold.scale() > 2)) {
            throw new IllegalArgumentException("Humidity threshold must be between 0 and 100 percent with at most two decimals.");
        }
        if (condition == AutomationRule.Condition.SCHEDULED_TIME && scheduledTime == null) {
            throw new IllegalArgumentException("Choose the scheduled time.");
        }
        if (scheduledTime != null && (scheduledTime.getSecond() != 0 || scheduledTime.getNano() != 0)) {
            throw new IllegalArgumentException("Scheduled time must be set to a whole minute.");
        }
        if (condition != AutomationRule.Condition.SCHEDULED_TIME && scheduledTime != null) {
            throw new IllegalArgumentException("Only scheduled-time rules can include a schedule time.");
        }
        if (condition != AutomationRule.Condition.TEMPERATURE_ABOVE
                && condition != AutomationRule.Condition.HUMIDITY_ABOVE && threshold != null) {
            throw new IllegalArgumentException("This condition does not use a threshold.");
        }
        if (condition == AutomationRule.Condition.MOTION_DETECTED && !"ON".equals(action)) {
            throw new IllegalArgumentException("Motion rules must turn their target device ON.");
        }
    }

    public static void validateTargetType(AutomationRule.Condition condition, String targetType, String action) {
        if (condition == null || targetType == null) {
            throw new IllegalArgumentException("Select a valid target device.");
        }
        String type = targetType.toLowerCase(java.util.Locale.ROOT);
        if (condition == AutomationRule.Condition.TEMPERATURE_ABOVE
                && !(type.contains("climate") || type.contains("cool"))) {
            throw new IllegalArgumentException("Temperature rules require a cooling or climate device.");
        }
        if (condition == AutomationRule.Condition.MOTION_DETECTED
                && (!type.contains("light") || !"ON".equals(action))) {
            throw new IllegalArgumentException("Motion rules must target a light and turn it ON.");
        }
    }
}
