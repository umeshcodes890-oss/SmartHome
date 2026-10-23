package com.smarthome.model;

import com.smarthome.service.ScheduleRecurrence;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

public record DeviceSchedule(
    long id,
    int deviceId,
    Long createdByUserId,
    String deviceName,
    String scheduleName,
    String action,
    ScheduleRecurrence.Pattern repeatPattern,
    LocalDate startDate,
    LocalTime localTime,
    ZoneId timeZone,
    Set<java.time.DayOfWeek> weekdays,
    Instant nextExecution,
    boolean enabled
) {
    public DeviceSchedule {
        weekdays = Set.copyOf(weekdays);
    }

    @Override public Set<java.time.DayOfWeek> weekdays() {
        return weekdays;
    }
}
