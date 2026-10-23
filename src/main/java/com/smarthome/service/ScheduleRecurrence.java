package com.smarthome.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

public final class ScheduleRecurrence {
    public enum Pattern {
        ONCE("Once"),
        DAILY("Daily"),
        WEEKDAYS("Selected weekdays");

        private final String label;
        Pattern(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private ScheduleRecurrence() {}

    public static Instant next(LocalDate startDate, LocalTime localTime, ZoneId zone,
            Pattern pattern, Set<DayOfWeek> weekdays, Instant after) {
        if (startDate == null || localTime == null || zone == null || pattern == null || after == null) {
            throw new IllegalArgumentException("A date, time, timezone, recurrence pattern, and reference instant are required.");
        }
        if (pattern == Pattern.WEEKDAYS && (weekdays == null || weekdays.isEmpty())) {
            throw new IllegalArgumentException("Choose at least one weekday.");
        }

        LocalDate candidate = startDate.isAfter(after.atZone(zone).toLocalDate())
            ? startDate : after.atZone(zone).toLocalDate();
        int searchDays = pattern == Pattern.ONCE ? 1 : pattern == Pattern.DAILY ? 2 : 8;
        for (int offset = 0; offset < searchDays; offset++) {
            LocalDate date = candidate.plusDays(offset);
            if (date.isBefore(startDate)) continue;
            if (pattern == Pattern.ONCE && !date.equals(startDate)) return null;
            if (pattern == Pattern.WEEKDAYS && !weekdays.contains(date.getDayOfWeek())) continue;
            Instant occurrence = resolve(LocalDateTime.of(date, localTime), zone);
            if (occurrence.isAfter(after)) return occurrence;
            if (pattern == Pattern.ONCE) return null;
        }
        if (pattern == Pattern.ONCE) return null;
        throw new IllegalStateException("Could not calculate the next schedule occurrence.");
    }

    public static Instant resolve(LocalDateTime localDateTime, ZoneId zone) {
        ZonedDateTime resolved = localDateTime.atZone(zone);
        return resolved.toInstant();
    }
}
