package com.smarthome.model;

import java.math.BigDecimal;
import java.time.Instant;

public record SensorAlert(long id, int roomId, String roomName, SensorReading.Type type,
        BigDecimal readingValue, BigDecimal thresholdValue, boolean simulated,
        Instant triggeredAt, Instant resolvedAt) {
    public boolean active() {
        return resolvedAt == null;
    }
}
