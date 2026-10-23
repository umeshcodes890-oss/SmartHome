package com.smarthome.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleRecurrenceTest {
    private static final ZoneId UTC = ZoneOffset.UTC;

    @Test
    void calculatesOneTimeOccurrenceAndRejectsAnOccurrenceInThePast() {
        LocalDate tomorrow = LocalDate.parse("2025-03-11");
        Instant after = Instant.parse("2025-03-10T12:00:00Z");

        assertEquals(Instant.parse("2025-03-11T07:30:00Z"),
            ScheduleRecurrence.next(tomorrow, LocalTime.of(7, 30), UTC,
                ScheduleRecurrence.Pattern.ONCE, Set.of(), after));
        assertNull(ScheduleRecurrence.next(tomorrow.minusDays(1), LocalTime.of(7, 30), UTC,
            ScheduleRecurrence.Pattern.ONCE, Set.of(), after));
    }

    @Test
    void dailyScheduleMovesToTomorrowAfterTodaysOccurrence() {
        assertEquals(Instant.parse("2025-03-11T07:30:00Z"),
            ScheduleRecurrence.next(LocalDate.parse("2025-03-01"), LocalTime.of(7, 30), UTC,
                ScheduleRecurrence.Pattern.DAILY, Set.of(), Instant.parse("2025-03-10T08:00:00Z")));
    }

    @Test
    void selectedWeekdaysHonorsStartDateAndFindsNextSelectedDay() {
        assertEquals(Instant.parse("2025-03-12T18:00:00Z"),
            ScheduleRecurrence.next(LocalDate.parse("2025-03-11"), LocalTime.of(18, 0), UTC,
                ScheduleRecurrence.Pattern.WEEKDAYS, Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                Instant.parse("2025-03-11T20:00:00Z")));
    }

    @Test
    void selectedWeekdaysRejectsAnEmptyDaySet() {
        assertThrows(IllegalArgumentException.class, () -> ScheduleRecurrence.next(
            LocalDate.parse("2025-03-10"), LocalTime.NOON, UTC,
            ScheduleRecurrence.Pattern.WEEKDAYS, Set.of(), Instant.parse("2025-03-09T00:00:00Z")));
    }

    @Test
    void selectedWeekdaysDoNotRunBeforeTheStartDate() {
        assertEquals(Instant.parse("2025-03-12T07:00:00Z"),
            ScheduleRecurrence.next(LocalDate.parse("2025-03-12"), LocalTime.of(7, 0), UTC,
                ScheduleRecurrence.Pattern.WEEKDAYS, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                Instant.parse("2025-03-10T06:00:00Z")));
    }

    @Test
    void daylightSavingGapMovesForwardByTheGap() {
        ZoneId newYork = ZoneId.of("America/New_York");

        assertEquals(Instant.parse("2025-03-09T07:30:00Z"),
            ScheduleRecurrence.resolve(LocalDateTime.parse("2025-03-09T02:30:00"), newYork));
    }

    @Test
    void daylightSavingOverlapUsesEarlierOffsetAndExecutesOnlyOnce() {
        ZoneId newYork = ZoneId.of("America/New_York");
        Instant firstOccurrence = Instant.parse("2025-11-02T05:30:00Z");

        assertEquals(firstOccurrence,
            ScheduleRecurrence.resolve(LocalDateTime.parse("2025-11-02T01:30:00"), newYork));
        assertEquals(Instant.parse("2025-11-03T06:30:00Z"),
            ScheduleRecurrence.next(LocalDate.parse("2025-11-02"), LocalTime.of(1, 30), newYork,
                ScheduleRecurrence.Pattern.DAILY, Set.of(), firstOccurrence));
    }
}
