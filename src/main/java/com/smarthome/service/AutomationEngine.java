package com.smarthome.service;

import com.smarthome.dao.AutomationDao;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AutomationEngine implements AutoCloseable {
    private final AutomationDao dao;
    private final Runnable onChange;
    private final Consumer<Throwable> onError;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "smart-home-automation-engine");
        thread.setDaemon(false);
        return thread;
    });
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean reportedError;
    private volatile boolean simulatedReadingsEnabled;

    public AutomationEngine(AutomationDao dao, Runnable onChange, Consumer<Throwable> onError) {
        this.dao = java.util.Objects.requireNonNull(dao);
        this.onChange = java.util.Objects.requireNonNull(onChange);
        this.onError = java.util.Objects.requireNonNull(onError);
    }

    public void start() {
        if (!started.compareAndSet(false, true)) return;
        executor.scheduleWithFixedDelay(this::tick, 0, 5, TimeUnit.SECONDS);
    }

    public void setSimulatedReadingsEnabled(boolean enabled) {
        simulatedReadingsEnabled = enabled;
    }

    private void tick() {
        try {
            int evaluated = dao.evaluateEnabled(Instant.now(), simulatedReadingsEnabled);
            if (evaluated > 0) onChange.run();
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
                throw new IllegalStateException("Automation engine did not stop within five seconds.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
