package com.smarthome.service;

import com.smarthome.model.EnergyBucket;
import com.smarthome.model.EnergyReport;
import com.smarthome.model.PowerBreakdown;
import com.smarthome.model.UsageSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class EnergyAnalytics {
    private static final BigDecimal WATT_NANOS_PER_KWH = new BigDecimal("3600000000000000");

    public enum Interval {
        DAILY("Daily"),
        WEEKLY("Weekly"),
        MONTHLY("Monthly");

        private final String displayName;
        Interval(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    private EnergyAnalytics() {}

    public static EnergyReport calculate(LocalDate fromDate, LocalDate toDate, Instant historyStartedAt,
            Instant rangeStart, Instant rangeEnd, ZoneId zone, Interval interval,
            List<UsageSession> sessions, List<PowerBreakdown> byRoom, List<PowerBreakdown> byType,
            BigDecimal tariffPerKwh) {
        BigDecimal activeWatts = byRoom.stream().map(PowerBreakdown::watts)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant effectiveStart = rangeStart.isBefore(historyStartedAt) ? historyStartedAt : rangeStart;
        boolean historyAvailable = effectiveStart.isBefore(rangeEnd);
        List<EnergyBucket> buckets = historyAvailable
            ? calculateBuckets(effectiveStart, rangeEnd, zone, interval, sessions)
            : List.of();
        BigDecimal total = buckets.stream().map(EnergyBucket::kilowattHours)
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(6, RoundingMode.HALF_UP);
        BigDecimal cost = tariffPerKwh == null || !historyAvailable ? null
            : total.multiply(tariffPerKwh).setScale(2, RoundingMode.HALF_UP);
        return new EnergyReport(fromDate, toDate, historyStartedAt, historyAvailable,
            activeWatts.setScale(2, RoundingMode.HALF_UP), List.copyOf(byRoom), List.copyOf(byType),
            buckets, total, tariffPerKwh, cost);
    }

    private static List<EnergyBucket> calculateBuckets(Instant start, Instant end, ZoneId zone,
            Interval interval, List<UsageSession> sessions) {
        ZonedDateTime cursor = start.atZone(zone);
        ZonedDateTime bucketCursor = bucketStart(cursor, interval);
        Map<LocalDate, BigDecimal> totals = new TreeMap<>();

        while (bucketCursor.toInstant().isBefore(end)) {
            ZonedDateTime next = nextBucket(bucketCursor, interval);
            Instant segmentStart = max(start, bucketCursor.toInstant());
            Instant segmentEnd = min(end, next.toInstant());
            BigDecimal bucketKwh = BigDecimal.ZERO;
            if (segmentStart.isBefore(segmentEnd)) {
                for (UsageSession session : sessions) {
                    Instant sessionStart = max(segmentStart, session.startedAt());
                    Instant sessionEnd = min(segmentEnd, session.endedAt() == null ? end : session.endedAt());
                    if (sessionStart.isBefore(sessionEnd)) {
                        bucketKwh = bucketKwh.add(energyKwh(session.powerWatts(), sessionStart, sessionEnd));
                    }
                }
            }
            totals.put(bucketCursor.toLocalDate(), bucketKwh.setScale(6, RoundingMode.HALF_UP));
            bucketCursor = next;
        }

        List<EnergyBucket> buckets = new ArrayList<>();
        totals.forEach((date, kwh) -> buckets.add(new EnergyBucket(date, label(date, interval), kwh)));
        return buckets;
    }

    private static BigDecimal energyKwh(BigDecimal watts, Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        BigDecimal nanos = BigDecimal.valueOf(duration.getSeconds()).movePointRight(9)
            .add(BigDecimal.valueOf(duration.getNano()));
        return watts.multiply(nanos).divide(WATT_NANOS_PER_KWH, 9, RoundingMode.HALF_UP);
    }

    private static ZonedDateTime bucketStart(ZonedDateTime time, Interval interval) {
        return switch (interval) {
            case DAILY -> time.toLocalDate().atStartOfDay(time.getZone());
            case WEEKLY -> time.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(time.getZone());
            case MONTHLY -> time.toLocalDate().withDayOfMonth(1).atStartOfDay(time.getZone());
        };
    }

    private static ZonedDateTime nextBucket(ZonedDateTime time, Interval interval) {
        return switch (interval) {
            case DAILY -> time.plusDays(1);
            case WEEKLY -> time.plusWeeks(1);
            case MONTHLY -> time.plusMonths(1);
        };
    }

    private static String label(LocalDate date, Interval interval) {
        return switch (interval) {
            case DAILY -> date.toString();
            case WEEKLY -> "Week of " + date;
            case MONTHLY -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
        };
    }

    private static Instant max(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }
}
