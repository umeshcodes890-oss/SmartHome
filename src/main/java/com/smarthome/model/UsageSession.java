package com.smarthome.model;

import java.math.BigDecimal;
import java.time.Instant;

public record UsageSession(
    int deviceId,
    String deviceName,
    String deviceType,
    int roomId,
    String roomName,
    BigDecimal powerWatts,
    Instant startedAt,
    Instant endedAt
) {}
