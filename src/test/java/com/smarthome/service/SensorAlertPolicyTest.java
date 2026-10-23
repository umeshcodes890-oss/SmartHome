package com.smarthome.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smarthome.model.SensorReading;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SensorAlertPolicyTest {
    @Test
    void thresholdsAreStrictAndRespectBothSupportedSensorTypes() {
        BigDecimal temperatureLimit = new BigDecimal("30");
        BigDecimal humidityLimit = new BigDecimal("70");

        assertFalse(SensorAlertPolicy.exceedsThreshold(SensorReading.Type.TEMPERATURE,
            new BigDecimal("30.00"), temperatureLimit));
        assertTrue(SensorAlertPolicy.exceedsThreshold(SensorReading.Type.TEMPERATURE,
            new BigDecimal("30.01"), temperatureLimit));
        assertFalse(SensorAlertPolicy.exceedsThreshold(SensorReading.Type.HUMIDITY,
            humidityLimit, humidityLimit));
        assertTrue(SensorAlertPolicy.exceedsThreshold(SensorReading.Type.HUMIDITY,
            new BigDecimal("70.01"), humidityLimit));
        assertFalse(SensorAlertPolicy.exceedsThreshold(SensorReading.Type.MOTION,
            BigDecimal.ONE, BigDecimal.ZERO));
    }

    @Test
    void raisesOnlyOnceUntilCurrentConditionHasCleared() {
        assertTrue(SensorAlertPolicy.shouldRaise(false, true));
        assertFalse(SensorAlertPolicy.shouldRaise(true, true));
        assertFalse(SensorAlertPolicy.shouldRaise(true, false));
        assertTrue(SensorAlertPolicy.shouldRaise(false, true));
    }
}
