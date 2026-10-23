package com.smarthome.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public record AutomationRule(long id, String name, boolean enabled, Condition condition,
        BigDecimal threshold, Integer sensorRoomId, String sensorRoomName, int targetDeviceId,
        String targetDeviceName, String targetDeviceType, String action, LocalTime scheduledTime,
        ZoneId timeZone, int cooldownSeconds, boolean lastConditionMet, Instant lastTriggeredAt,
        LocalDate lastScheduledDate) {
    public enum Condition {
        TEMPERATURE_ABOVE("Temperature above"),
        HUMIDITY_ABOVE("Humidity above"),
        MOTION_DETECTED("Motion detected"),
        SCHEDULED_TIME("Scheduled time");

        private final String displayName;

        Condition(String displayName) { this.displayName = displayName; }

        @Override public String toString() { return displayName; }

        public boolean usesSensor() {
            return this == TEMPERATURE_ABOVE || this == HUMIDITY_ABOVE || this == MOTION_DETECTED;
        }
    }
}
