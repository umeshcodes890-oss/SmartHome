package com.smarthome.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smarthome.model.AutomationRule;
import com.smarthome.model.SensorReading;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AutomationRuleEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void temperatureThresholdIsStrictAndAboveThresholdTriggers() {
        AutomationRule rule = rule(AutomationRule.Condition.TEMPERATURE_ABOVE,
            BigDecimal.valueOf(25), false, null, null, 0);

        var boundary = AutomationRuleEvaluator.evaluate(rule, reading(SensorReading.Type.TEMPERATURE, "25"), NOW);
        var above = AutomationRuleEvaluator.evaluate(rule, reading(SensorReading.Type.TEMPERATURE, "25.1"), NOW);

        assertFalse(boundary.conditionMet());
        assertFalse(boundary.trigger());
        assertTrue(above.conditionMet());
        assertTrue(above.trigger());
    }

    @Test
    void disabledRuleDoesNotTriggerEvenWhenSensorConditionIsMet() {
        AutomationRule rule = new AutomationRule(1, "Disabled", false,
            AutomationRule.Condition.TEMPERATURE_ABOVE, BigDecimal.valueOf(20), 3, "Living Room",
            8, "Air conditioner", "Climate", "ON", null, UTC, 0, false, null, null);

        var decision = AutomationRuleEvaluator.evaluate(rule,
            reading(SensorReading.Type.TEMPERATURE, "30"), NOW);

        assertFalse(decision.conditionMet());
        assertFalse(decision.trigger());
        assertTrue(decision.explanation().contains("disabled"));
    }

    @Test
    void remainsQuietWhileConditionStaysTrue() {
        AutomationRule rule = rule(AutomationRule.Condition.HUMIDITY_ABOVE,
            BigDecimal.valueOf(60), true, NOW.minusSeconds(30), null, 10);

        var decision = AutomationRuleEvaluator.evaluate(rule, reading(SensorReading.Type.HUMIDITY, "70"), NOW);

        assertTrue(decision.conditionMet());
        assertFalse(decision.trigger());
        assertFalse(decision.cooldownSuppressed());
    }

    @Test
    void cooldownSuppressesAnewConditionTransition() {
        AutomationRule rule = rule(AutomationRule.Condition.TEMPERATURE_ABOVE,
            BigDecimal.valueOf(20), false, NOW.minusSeconds(10), null, 60);

        var decision = AutomationRuleEvaluator.evaluate(rule,
            reading(SensorReading.Type.TEMPERATURE, "21"), NOW);

        assertTrue(decision.conditionMet());
        assertFalse(decision.trigger());
        assertTrue(decision.cooldownSuppressed());
    }

    @Test
    void scheduledRuleTriggersOnlyOnceForTheLocalDate() {
        LocalDate today = NOW.atZone(UTC).toLocalDate();
        AutomationRule first = new AutomationRule(1, "Evening", true,
            AutomationRule.Condition.SCHEDULED_TIME, null, null, null, 8, "Lamp", "Light",
            "OFF", LocalTime.of(12, 0), UTC, 0, false, null, null);
        var due = AutomationRuleEvaluator.evaluate(first, null, NOW);
        AutomationRule alreadyRun = new AutomationRule(1, "Evening", true,
            AutomationRule.Condition.SCHEDULED_TIME, null, null, null, 8, "Lamp", "Light",
            "OFF", LocalTime.of(12, 0), UTC, 0, false, NOW, today);

        assertTrue(due.trigger());
        assertFalse(AutomationRuleEvaluator.evaluate(alreadyRun, null, NOW).trigger());
    }

    @Test
    void scheduledRuleMovesForwardAcrossDaylightSavingGap() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        AutomationRule rule = new AutomationRule(1, "Gap rule", true,
            AutomationRule.Condition.SCHEDULED_TIME, null, null, null, 8, "Lamp", "Light",
            "ON", LocalTime.of(2, 30), berlin, 0, false, null, null);
        Instant afterGap = Instant.parse("2026-03-29T01:30:00Z");

        assertTrue(AutomationRuleEvaluator.evaluate(rule, null, afterGap).trigger());
    }

    @Test
    void rejectsIncompatibleTargetsAndActions() {
        assertThrows(IllegalArgumentException.class,
            () -> AutomationRuleValidator.validateTargetType(
                AutomationRule.Condition.TEMPERATURE_ABOVE, "Entertainment", "ON"));
        assertThrows(IllegalArgumentException.class,
            () -> AutomationRuleValidator.validateTargetType(
                AutomationRule.Condition.MOTION_DETECTED, "Light", "OFF"));
    }

    @Test
    void rejectsMissingSensorRoomAndInvalidThreshold() {
        assertThrows(IllegalArgumentException.class, () -> AutomationRuleValidator.validate(
            "Hot room", AutomationRule.Condition.TEMPERATURE_ABOVE, BigDecimal.valueOf(25),
            null, 1, "ON", null, UTC, 0));
        assertThrows(IllegalArgumentException.class, () -> AutomationRuleValidator.validate(
            "Too hot", AutomationRule.Condition.TEMPERATURE_ABOVE, BigDecimal.valueOf(61),
            1, 1, "ON", null, UTC, 0));
        assertThrows(IllegalArgumentException.class, () -> AutomationRuleValidator.validate(
            "Invalid time", AutomationRule.Condition.SCHEDULED_TIME, null,
            null, 1, "ON", LocalTime.of(12, 0, 1), UTC, 0));
    }

    private static AutomationRule rule(AutomationRule.Condition condition, BigDecimal threshold,
            boolean lastConditionMet, Instant lastTriggeredAt, LocalDate lastScheduledDate,
            int cooldownSeconds) {
        return new AutomationRule(1, "Test rule", true, condition, threshold, 3, "Living Room",
            8, "Target", condition == AutomationRule.Condition.TEMPERATURE_ABOVE ? "Climate" : "Other",
            "ON", null, UTC, cooldownSeconds, lastConditionMet, lastTriggeredAt, lastScheduledDate);
    }

    private static SensorReading reading(SensorReading.Type type, String value) {
        return new SensorReading(1, 3, "Living Room", type, new BigDecimal(value), true, NOW);
    }
}
