package com.smarthome.model;

import java.time.Instant;

public record ScheduleExecution(
    long id,
    Long scheduleId,
    String scheduleName,
    String deviceName,
    String action,
    Instant occurrenceAt,
    Instant executedAt,
    String result,
    String details
) {}
