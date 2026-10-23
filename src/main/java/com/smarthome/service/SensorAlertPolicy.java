package com.smarthome.service;

import com.smarthome.model.SensorReading;
import java.math.BigDecimal;

public final class SensorAlertPolicy {
    private SensorAlertPolicy() {}

    public static boolean exceedsThreshold(SensorReading.Type type, BigDecimal value, BigDecimal threshold) {
        if (type != SensorReading.Type.TEMPERATURE && type != SensorReading.Type.HUMIDITY) return false;
        return value.compareTo(threshold) > 0;
    }

    public static boolean shouldRaise(boolean alreadyActive, boolean exceedsThreshold) {
        return !alreadyActive && exceedsThreshold;
    }
}
