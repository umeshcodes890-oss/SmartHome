package com.smarthome.service;

import com.smarthome.dao.ScheduleDao;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ScheduleService implements AutoCloseable {
    private final ScheduleDao dao;
    private final Runnable onChange;
    private final Consumer<Throwable> onError;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "smart-home-scheduler");
        thread.setDaemon(false);
        return thread;
    });
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean initialized;
    private volatile boolean reportedError;

    public ScheduleService(ScheduleDao dao, Runnable onChange, Consumer<Throwable> onError) {
        this.dao = dao;
        this.onChange = onChange;
        this.onError = onError;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) return;
        executor.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    private void tick() {
        try {
            if (!initialized) {
                dao.reconcileOnStartup(Instant.now());
                initialized = true;
                reportedError = false;
                onChange.run();
                return;
            }
            if (dao.executeDue(Instant.now()) > 0) onChange.run();
            reportedError = false;
        } catch (SQLException | RuntimeException ex) {
            if (!reportedError) onError.accept(ex);
            reportedError = true;
        }
    }

    @Override public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Schedule service did not stop within five seconds.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
