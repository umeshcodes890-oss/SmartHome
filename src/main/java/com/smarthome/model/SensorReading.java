package com.smarthome.model;

import java.math.BigDecimal;
import java.time.Instant;

public record SensorReading(long id, int roomId, String roomName, Type type, BigDecimal value,
        boolean simulated, Instant recordedAt) {
    public enum Type {
        TEMPERATURE,
        HUMIDITY,
        MOTION
    }

    public String formattedValue() {
        return switch (type) {
            case TEMPERATURE -> value.stripTrailingZeros().toPlainString() + " °C";
            case HUMIDITY -> value.stripTrailingZeros().toPlainString() + " %";
            case MOTION -> value.signum() > 0 ? "Detected" : "No motion";
        };
    }
}
