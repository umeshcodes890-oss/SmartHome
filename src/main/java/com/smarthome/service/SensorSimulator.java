package com.smarthome.service;

import com.smarthome.dao.RoomDao;
import com.smarthome.dao.SensorDao;
import com.smarthome.model.Room;
import com.smarthome.model.SensorReading;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SensorSimulator implements AutoCloseable {
    public static final int MIN_INTERVAL_SECONDS = 10;
    public static final int MAX_INTERVAL_SECONDS = 3600;

    private final RoomSource rooms;
    private final ReadingWriter readings;
    private final Consumer<Throwable> onError;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "smart-home-sensor-simulator");
        thread.setDaemon(true);
        return thread;
    });
    private final Random random = new Random();
    private final Map<Integer, RoomValues> roomValues = new HashMap<>();
    private ScheduledFuture<?> task;
    private int intervalSeconds = 30;
    private boolean reportedError;
    private boolean closed;

    public SensorSimulator(RoomDao rooms, SensorDao sensors, Consumer<Throwable> onError) {
        this(java.util.Objects.requireNonNull(rooms)::findAll,
            java.util.Objects.requireNonNull(sensors)::saveSimulated, onError);
    }

    SensorSimulator(RoomSource rooms, ReadingWriter readings, Consumer<Throwable> onError) {
        this.rooms = java.util.Objects.requireNonNull(rooms);
        this.readings = java.util.Objects.requireNonNull(readings);
        this.onError = java.util.Objects.requireNonNull(onError);
    }

    public synchronized void configure(boolean enabled, int intervalSeconds) {
        if (closed) throw new IllegalStateException("Sensor simulator has been closed.");
        if (intervalSeconds < MIN_INTERVAL_SECONDS || intervalSeconds > MAX_INTERVAL_SECONDS) {
            throw new IllegalArgumentException("Sensor interval must be between 10 and 3600 seconds.");
        }
        this.intervalSeconds = intervalSeconds;
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (enabled) {
            task = executor.scheduleWithFixedDelay(this::sampleSafely, 0, intervalSeconds, TimeUnit.SECONDS);
        }
    }

    private void sampleSafely() {
        try {
            List<Room> roomList = rooms.findAll();
            Instant now = Instant.now();
            List<SensorReading> batch = new ArrayList<>(roomList.size() * 3);
            synchronized (roomValues) {
                for (Room room : roomList) {
                    RoomValues values = roomValues.computeIfAbsent(room.id(), ignored ->
                        new RoomValues(20.0 + random.nextDouble() * 5.0,
                            35.0 + random.nextDouble() * 25.0));
                    values.temperature = clamp(values.temperature + random.nextGaussian() * 0.35, 16, 32);
                    values.humidity = clamp(values.humidity + random.nextGaussian() * 2.0, 25, 75);
                    boolean motion = random.nextDouble() < 0.08;
                    batch.add(reading(room, SensorReading.Type.TEMPERATURE, values.temperature, now));
                    batch.add(reading(room, SensorReading.Type.HUMIDITY, values.humidity, now));
                    batch.add(reading(room, SensorReading.Type.MOTION, motion ? 1 : 0, now));
                }
            }
            readings.save(batch);
            synchronized (this) {
                reportedError = false;
            }
        } catch (SQLException | RuntimeException ex) {
            synchronized (this) {
                if (!reportedError) onError.accept(ex);
                reportedError = true;
            }
        }
    }

    private static SensorReading reading(Room room, SensorReading.Type type, double value, Instant at) {
        return new SensorReading(0, room.id(), room.name(), type,
            BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP), true, at);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            if (task != null) task.cancel(true);
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Sensor simulator did not stop within five seconds.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class RoomValues {
        private double temperature;
        private double humidity;
        RoomValues(double temperature, double humidity) {
            this.temperature = temperature;
            this.humidity = humidity;
        }
    }

    @FunctionalInterface
    interface RoomSource {
        List<Room> findAll() throws SQLException;
    }

    @FunctionalInterface
    interface ReadingWriter {
        void save(List<SensorReading> readings) throws SQLException;
    }
}
