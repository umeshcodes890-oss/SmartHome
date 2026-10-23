package com.smarthome.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.smarthome.model.EnergyReport;
import com.smarthome.model.UsageSession;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnergyAnalyticsTest {
    @Test
    void computesDailyEnergyAndCostFromRatedPowerAndRuntime() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-01-02T00:00:00Z");
        EnergyReport report = calculate(start, end, start, end, EnergyAnalytics.Interval.DAILY,
            new BigDecimal("0.25"), new BigDecimal("100"));

        assertEquals(new BigDecimal("2.400000"), report.estimatedKilowattHours());
        assertEquals(new BigDecimal("0.60"), report.estimatedCost());
        assertEquals(1, report.consumption().size());
    }

    @Test
    void clipsUsageToHistoryStartAndWeeklyBucket() {
        Instant rangeStart = Instant.parse("2025-01-06T00:00:00Z");
        Instant historyStart = Instant.parse("2025-01-06T12:00:00Z");
        Instant end = Instant.parse("2025-01-13T00:00:00Z");
        EnergyReport report = calculate(rangeStart, end, historyStart, end,
            EnergyAnalytics.Interval.WEEKLY, null, new BigDecimal("50"));

        assertEquals(new BigDecimal("7.800000"), report.estimatedKilowattHours());
        assertEquals(1, report.consumption().size());
        assertEquals("Week of 2025-01-06", report.consumption().getFirst().label());
        assertNull(report.estimatedCost());
    }

    @Test
    void reportsNoHistoricalDataBeforeCollectionStarted() {
        Instant rangeStart = Instant.parse("2025-01-01T00:00:00Z");
        Instant rangeEnd = Instant.parse("2025-01-02T00:00:00Z");
        Instant historyStart = Instant.parse("2025-02-01T00:00:00Z");
        EnergyReport report = calculate(rangeStart, rangeEnd, historyStart, rangeEnd,
            EnergyAnalytics.Interval.DAILY, null, new BigDecimal("100"));

        assertFalse(report.historyAvailable());
        assertEquals(BigDecimal.ZERO.setScale(6), report.estimatedKilowattHours());
        assertEquals(List.of(), report.consumption());
        assertNull(report.estimatedCost());
    }

    @Test
    void splitsMonthlyEstimateAcrossCalendarMonths() {
        Instant start = Instant.parse("2025-01-31T12:00:00Z");
        Instant end = Instant.parse("2025-02-01T12:00:00Z");
        EnergyReport report = calculate(start, end, start, end, EnergyAnalytics.Interval.MONTHLY,
            null, new BigDecimal("100"));

        assertEquals(2, report.consumption().size());
        assertEquals("2025-01", report.consumption().get(0).label());
        assertEquals("2025-02", report.consumption().get(1).label());
        assertEquals(new BigDecimal("2.400000"), report.estimatedKilowattHours());
    }

    private EnergyReport calculate(Instant rangeStart, Instant rangeEnd, Instant historyStart, Instant sessionEnd,
            EnergyAnalytics.Interval interval, BigDecimal tariff, BigDecimal watts) {
        LocalDate from = rangeStart.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate to = rangeEnd.minusNanos(1).atZone(ZoneOffset.UTC).toLocalDate();
        UsageSession session = new UsageSession(7, "Test device", "Light", 3, "Office",
            watts, rangeStart, sessionEnd);
        return EnergyAnalytics.calculate(from, to, historyStart, rangeStart, rangeEnd,
            ZoneOffset.UTC, interval, List.of(session), List.of(), List.of(), tariff);
    }
}
