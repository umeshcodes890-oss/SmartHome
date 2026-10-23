package com.smarthome.service;

import com.smarthome.model.AutomationRule;
import com.smarthome.model.SensorReading;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class AutomationRuleEvaluator {
    public record Decision(boolean conditionMet, boolean trigger, boolean cooldownSuppressed,
            LocalDate scheduledDate, String explanation) {}

    private AutomationRuleEvaluator() {}

    public static Decision evaluate(AutomationRule rule, SensorReading reading, Instant now) {
        if (rule == null || now == null) throw new IllegalArgumentException("Rule and evaluation time are required.");
        if (!rule.enabled()) return new Decision(false, false, false, null, "Rule is disabled.");

        if (rule.condition() == AutomationRule.Condition.SCHEDULED_TIME) {
            ZoneId zone = rule.timeZone();
            LocalDate date = now.atZone(zone).toLocalDate();
            LocalTime currentMinute = now.atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES);
            LocalTime effectiveScheduledMinute = date.atTime(rule.scheduledTime()).atZone(zone)
                .toLocalTime().truncatedTo(ChronoUnit.MINUTES);
            boolean due = currentMinute.equals(effectiveScheduledMinute)
                && !date.equals(rule.lastScheduledDate());
            if (!due) return new Decision(false, false, false, null, "Scheduled time is not due.");
            boolean suppressed = inCooldown(rule, now);
            return new Decision(true, !suppressed, suppressed, date,
                suppressed ? "Scheduled occurrence suppressed by cooldown." : "Scheduled occurrence is due.");
        }

        boolean met = reading != null && rule.sensorRoomId() != null
            && reading.roomId() == rule.sensorRoomId()
            && switch (rule.condition()) {
                case TEMPERATURE_ABOVE -> reading.type() == SensorReading.Type.TEMPERATURE
                    && reading.value().compareTo(rule.threshold()) > 0;
                case HUMIDITY_ABOVE -> reading.type() == SensorReading.Type.HUMIDITY
                    && reading.value().compareTo(rule.threshold()) > 0;
                case MOTION_DETECTED -> reading.type() == SensorReading.Type.MOTION
                    && reading.value().signum() > 0;
                case SCHEDULED_TIME -> false;
            };
        if (!met) return new Decision(false, false, false, null, "Condition is not met.");
        if (rule.lastConditionMet()) {
            return new Decision(true, false, false, null, "Condition remains true; waiting for a state transition.");
        }
        boolean suppressed = inCooldown(rule, now);
        return new Decision(true, !suppressed, suppressed, null,
            suppressed ? "Condition transition suppressed by cooldown." : "Condition changed to true.");
    }

    private static boolean inCooldown(AutomationRule rule, Instant now) {
        return rule.lastTriggeredAt() != null && rule.cooldownSeconds() > 0
            && rule.lastTriggeredAt().plusSeconds(rule.cooldownSeconds()).isAfter(now);
    }
}
