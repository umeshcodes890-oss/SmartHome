package com.smarthome.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record SensorAlertSettings(BigDecimal temperatureThreshold, BigDecimal humidityThreshold) {
    public SensorAlertSettings {
        Objects.requireNonNull(temperatureThreshold, "temperatureThreshold");
        Objects.requireNonNull(humidityThreshold, "humidityThreshold");
        try {
            temperatureThreshold = temperatureThreshold.setScale(2, RoundingMode.UNNECESSARY);
            humidityThreshold = humidityThreshold.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Thresholds may have at most two decimal places.", ex);
        }
        if (temperatureThreshold.compareTo(new BigDecimal("-20")) < 0
                || temperatureThreshold.compareTo(new BigDecimal("60")) > 0) {
            throw new IllegalArgumentException("Temperature threshold must be between -20 and 60 °C.");
        }
        if (humidityThreshold.compareTo(BigDecimal.ZERO) < 0
                || humidityThreshold.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Humidity threshold must be between 0 and 100 %.");
        }
    }
}
