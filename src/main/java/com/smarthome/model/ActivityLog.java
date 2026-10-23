package com.smarthome.model;

import java.time.Instant;

public record ActivityLog(
    long id,
    String eventType,
    Long userId,
    Long deviceId,
    String deviceName,
    Long scheduleId,
    Instant occurredAt,
    Outcome outcome,
    String details
) {
    public ActivityLog(long id, String eventType, Long deviceId, String deviceName, Long scheduleId,
            Instant occurredAt, Outcome outcome, String details) {
        this(id, eventType, null, deviceId, deviceName, scheduleId, occurredAt, outcome, details);
    }

    public enum Outcome {
        SUCCESS,
        FAILED,
        MISSED
    }

    public static ActivityLog success(String eventType, Long deviceId, String deviceName, Long scheduleId,
            Instant occurredAt, String details) {
        return success(eventType, null, deviceId, deviceName, scheduleId, occurredAt, details);
    }

    public static ActivityLog success(String eventType, Long userId, Long deviceId, String deviceName,
            Long scheduleId, Instant occurredAt, String details) {
        return new ActivityLog(0, eventType, userId, deviceId, deviceName, scheduleId,
            occurredAt, Outcome.SUCCESS, details);
    }

    public static ActivityLog failure(String eventType, Long deviceId, String deviceName, Long scheduleId,
            Instant occurredAt, String details) {
        return new ActivityLog(0, eventType, null, deviceId, deviceName, scheduleId, occurredAt, Outcome.FAILED, details);
    }

    public static ActivityLog missed(String eventType, Long deviceId, String deviceName, Long scheduleId,
            Instant occurredAt, String details) {
        return new ActivityLog(0, eventType, null, deviceId, deviceName, scheduleId, occurredAt, Outcome.MISSED, details);
    }
}