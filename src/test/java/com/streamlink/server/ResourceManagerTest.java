package com.streamlink.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ResourceManagerTest {

    private final ResourceManager resourceManager =
            ResourceManager.getInstance();

    @Test
    public void admitsAtMostThreeConcurrentReservations() throws Exception {
        int threadCount = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<ResourceManager.StreamReservation> reservations =
                new ArrayList<>();

        try {
            List<java.util.concurrent.Future<ResourceManager.StreamReservation>>
                    futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return resourceManager.tryReserveStream();
                }));
            }
            start.countDown();
            for (var future : futures) {
                ResourceManager.StreamReservation reservation = future.get();
                if (reservation != null) {
                    reservations.add(reservation);
                }
            }

            assertEquals(3, reservations.size());
            assertEquals(3, resourceManager.getActiveStreams());
            assertTrue(resourceManager.getActiveStreams()
                    <= resourceManager.getMaxStreams());
        } finally {
            for (ResourceManager.StreamReservation reservation : reservations) {
                reservation.close();
            }
            executor.shutdownNow();
            assertEquals(0, resourceManager.getActiveStreams());
        }
    }

    @Test
    public void reservationReleaseIsIdempotent() {
        ResourceManager.StreamReservation reservation =
                resourceManager.tryReserveStream();
        assertNotNull(reservation);
        assertEquals(1, resourceManager.getActiveStreams());

        reservation.close();
        reservation.close();

        assertEquals(0, resourceManager.getActiveStreams());
        ResourceManager.StreamReservation replacement =
                resourceManager.tryReserveStream();
        assertNotNull(replacement);
        replacement.close();
    }

    @Test
    public void legacyStartAndEndCannotReleaseAnotherThreadReservation() {
        assertTrue(resourceManager.tryStartStream());
        assertTrue(resourceManager.tryStartStream());
        assertEquals(2, resourceManager.getActiveStreams());

        Thread unrelated = new Thread(resourceManager::endStream);
        unrelated.start();
        try {
            unrelated.join(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(2, resourceManager.getActiveStreams());
        resourceManager.endStream();
        resourceManager.endStream();
        resourceManager.endStream();
        assertEquals(0, resourceManager.getActiveStreams());
        resourceManager.endStream();
    }
}
