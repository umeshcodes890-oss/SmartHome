package com.smarthome.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smarthome.model.Room;
import com.smarthome.model.SensorReading;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SensorSimulatorTest {
    @Test
    void canStartStopAndCloseWithoutLeavingRepeatedTasksRunning() throws Exception {
        AtomicInteger batches = new AtomicInteger();
        AtomicInteger activeWrites = new AtomicInteger();
        AtomicInteger maximumConcurrentWrites = new AtomicInteger();
        CountDownLatch firstSample = new CountDownLatch(1);
        SensorSimulator simulator = new SensorSimulator(
            () -> List.of(new Room(1, "Living Room", 1, 0, 0)),
            readings -> {
                int active = activeWrites.incrementAndGet();
                maximumConcurrentWrites.accumulateAndGet(active, Math::max);
                batches.incrementAndGet();
                firstSample.countDown();
                try {
                    Thread.sleep(40);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    activeWrites.decrementAndGet();
                }
            }, failure -> {
            throw new AssertionError("Unexpected simulator error.", failure);
        });

        simulator.configure(true, SensorSimulator.MAX_INTERVAL_SECONDS);
        assertTrue(firstSample.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < 4; i++) {
            simulator.configure(true, SensorSimulator.MAX_INTERVAL_SECONDS);
        }
        Thread.sleep(200);
        assertEquals(1, maximumConcurrentWrites.get());
        simulator.configure(false, SensorSimulator.MAX_INTERVAL_SECONDS);
        simulator.configure(false, SensorSimulator.MAX_INTERVAL_SECONDS);
        int stoppedCount = batches.get();
        Thread.sleep(100);
        assertEquals(stoppedCount, batches.get());

        simulator.close();
        assertThrows(IllegalStateException.class,
            () -> simulator.configure(true, SensorSimulator.MAX_INTERVAL_SECONDS));
    }
}
