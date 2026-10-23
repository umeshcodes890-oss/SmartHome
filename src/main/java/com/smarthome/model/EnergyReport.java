package com.smarthome.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EnergyReport(
    LocalDate fromDate,
    LocalDate toDate,
    Instant historyStartedAt,
    boolean historyAvailable,
    BigDecimal activeRatedWatts,
    List<PowerBreakdown> activeByRoom,
    List<PowerBreakdown> activeByType,
    List<EnergyBucket> consumption,
    BigDecimal estimatedKilowattHours,
    BigDecimal tariffPerKwh,
    BigDecimal estimatedCost
) {}
