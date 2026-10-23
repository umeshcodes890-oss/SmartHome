package com.smarthome.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EnergyBucket(LocalDate startDate, String label, BigDecimal kilowattHours) {}
