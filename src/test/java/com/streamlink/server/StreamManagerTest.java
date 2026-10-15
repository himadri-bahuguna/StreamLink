package com.streamlink.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import com.streamlink.server.FileManager;

public class StreamManagerTest {

    @Test
    public void insertFailureReleasesReservationAndPreventsTransfer()
            throws Exception {
        ResourceManager resources = ResourceManager.getInstance();
        FakeStore store = new FakeStore();
        store.insertFailure = new Exception("insert failed");
        StreamManager manager = newManager(store);

        try {
            manager.startStream(10, 1, "video.mp4");
        } catch (Exception expected) {
            assertEquals("insert failed", expected.getMessage());
        }

        assertEquals(0, resources.getActiveStreams());
    }

    @Test
    public void fourthStreamIsRejectedBeforeInsertion() throws Exception {
        ResourceManager resources = ResourceManager.getInstance();
        FakeStore store = new FakeStore();
        StreamManager manager = newManager(store);
        ResourceManager.StreamReservation first =
                resources.tryReserveStream();
        ResourceManager.StreamReservation second =
                resources.tryReserveStream();
        ResourceManager.StreamReservation third =
                resources.tryReserveStream();
        try {
            try {
                manager.startStream(10, 1, "video.mp4");
            } catch (Exception expected) {
                assertEquals("Maximum number of active streams reached.",
                        expected.getMessage());
            }

            assertEquals(0, store.insertCalls);
            assertEquals(3, resources.getActiveStreams());
        } finally {
            first.close();
            second.close();
            third.close();
        }
        assertEquals(0, resources.getActiveStreams());
    }

    @Test
    public void stopDuringTransferDoesNotReleaseSlot() throws Exception {
        ResourceManager resources = ResourceManager.getInstance();
        FakeStore store = new FakeStore();
        StreamManager manager = newManager(store);
        StreamManager.ActiveStream stream =
                manager.startStream(10, 1, "video.mp4");
        try {
            assertEquals(StreamManager.StopResult.TRANSFER_IN_PROGRESS,
                    manager.stopStream(10, stream.streamId()));
            assertEquals(1, resources.getActiveStreams());
            assertEquals(0, store.finalizeCalls);
            ResourceManager.StreamReservation second =
                    resources.tryReserveStream();
            ResourceManager.StreamReservation third =
                    resources.tryReserveStream();
            try {
                assertEquals(null, resources.tryReserveStream());
            } finally {
                second.close();
                third.close();
            }
        } finally {
            manager.finishStream(stream);
        }

        assertEquals(0, resources.getActiveStreams());
        assertEquals(1, store.finalizeCalls);
    }

    @Test
    public void finalizationFailureKeepsStreamTrackedAndReservationHeld()
            throws Exception {
        ResourceManager resources = ResourceManager.getInstance();
        FakeStore store = new FakeStore();
        StreamManager manager = newManager(store);
        StreamManager.ActiveStream stream =
                manager.startStream(10, 1, "video.mp4");
        store.finalizeFailure = new Exception("database unavailable");
        manager.finishStream(stream);

        assertEquals(StreamManager.StopResult.FINALIZATION_FAILED,
                manager.stopStream(10, stream.streamId()));
        assertEquals(1, resources.getActiveStreams());

        store.finalizeFailure = null;
        assertEquals(StreamManager.StopResult.STOPPED,
                manager.stopStream(10, stream.streamId()));
        assertEquals(0, resources.getActiveStreams());
        assertEquals(3, store.finalizeCalls);
    }

    @Test
    public void unauthorizedAndRepeatedStopsAreSafe() throws Exception {
        ResourceManager resources = ResourceManager.getInstance();
        FakeStore store = new FakeStore();
        StreamManager manager = newManager(store);
        StreamManager.ActiveStream stream =
                manager.startStream(10, 1, "video.mp4");

        assertEquals(StreamManager.StopResult.NOT_FOUND_OR_UNAUTHORIZED,
                manager.stopStream(11, stream.streamId()));
        assertEquals(1, resources.getActiveStreams());
        manager.finishStream(stream);
        assertEquals(StreamManager.StopResult.NOT_FOUND_OR_UNAUTHORIZED,
                manager.stopStream(10, stream.streamId()));
        assertEquals(StreamManager.StopResult.NOT_FOUND_OR_UNAUTHORIZED,
                manager.stopStream(10, stream.streamId()));
        assertEquals(0, resources.getActiveStreams());
        assertEquals(1, store.finalizeCalls);
    }

    @Test
    public void concurrentStopAndCompletionFinalizeOnce() throws Exception {
        ResourceManager resources = ResourceManager.getInstance();
        FakeStore store = new FakeStore();
        StreamManager manager = newManager(store);
        StreamManager.ActiveStream stream =
                manager.startStream(10, 1, "video.mp4");
        manager.finishStream(stream);

        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<StreamManager.StopResult> result =
                new AtomicReference<>();
        Thread stopper = new Thread(() -> {
            try {
                start.await();
                result.set(manager.stopStream(10, stream.streamId()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        stopper.start();
        start.countDown();
        manager.finishStream(stream);
        stopper.join();

        assertNotNull(result.get());
        assertEquals(1, store.finalizeCalls);
        assertEquals(0, resources.getActiveStreams());
    }

    @Test
    public void concurrentStopAndCompletionWhileTransferringFinalizeOnce()
            throws Exception {
        ResourceManager resources = ResourceManager.getInstance();
        FakeStore store = new FakeStore();
        StreamManager manager = newManager(store);
        StreamManager.ActiveStream stream =
                manager.startStream(10, 1, "video.mp4");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<StreamManager.StopResult> stopResult =
                new AtomicReference<>();
        Thread stopper = new Thread(() -> {
            await(ready, go);
            stopResult.set(manager.stopStream(10, stream.streamId()));
        });
        Thread finisher = new Thread(() -> {
            await(ready, go);
            manager.finishStream(stream);
        });
        try {
            stopper.start();
            finisher.start();
            assertEquals(true, ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            go.countDown();
            stopper.join(5000);
            finisher.join(5000);

            assertNotNull(stopResult.get());
            assertTrue(stopResult.get() == StreamManager.StopResult.TRANSFER_IN_PROGRESS
                || stopResult.get() == StreamManager.StopResult.STOPPED
                || stopResult.get()
                == StreamManager.StopResult.NOT_FOUND_OR_UNAUTHORIZED);
            assertEquals(1, store.finalizeCalls);
            assertEquals(0, resources.getActiveStreams());
            assertEquals(StreamManager.StopResult.NOT_FOUND_OR_UNAUTHORIZED,
                    manager.stopStream(10, stream.streamId()));
        } finally {
            go.countDown();
            stopper.join(5000);
            finisher.join(5000);
            if (resources.getActiveStreams() > 0) {
                manager.finishStream(stream);
            }
        }
    }

    @Test
    public void finalizationFailureCanBeRetriedSuccessfully()
            throws Exception {
        ResourceManager resources = ResourceManager.getInstance();
        FakeStore store = new FakeStore();
        StreamManager manager = newManager(store);
        StreamManager.ActiveStream stream =
                manager.startStream(10, 1, "video.mp4");
        store.finalizeFailure = new Exception("database unavailable");

        try {
            manager.finishStream(stream);
            assertEquals(1, store.finalizeCalls);
            assertEquals(0, store.successfulFinalizeCalls);
            assertEquals(1, resources.getActiveStreams());

            store.finalizeFailure = null;
            assertEquals(StreamManager.StopResult.STOPPED,
                    manager.stopStream(10, stream.streamId()));
            assertEquals(2, store.finalizeCalls);
            assertEquals(1, store.successfulFinalizeCalls);
            assertEquals(0, resources.getActiveStreams());
            assertEquals(StreamManager.StopResult.NOT_FOUND_OR_UNAUTHORIZED,
                    manager.stopStream(10, stream.streamId()));
        } finally {
            manager.finishStream(stream);
        }
    }

    private static void await(
            CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            go.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test thread interrupted.", e);
        }
    }

    private StreamManager newManager(FakeStore store) throws Exception {
        File directory = Files.createTempDirectory("stream-manager-test")
                .toFile();
        Files.writeString(new File(directory, "video.mp4").toPath(), "video");
        return new StreamManager(
                store, new FileManager(directory),
                ResourceManager.getInstance(), true);
    }

    private static final class FakeStore implements StreamRecordStore {
        private Exception insertFailure;
        private Exception finalizeFailure;
        private int insertCalls;
        private int finalizeCalls;
        private int successfulFinalizeCalls;
        private int nextStreamId = 100;

        @Override
        public String findStoredPath(int videoId) {
            return "video.mp4";
        }

        @Override
        public int insert(int userId, int videoId) throws Exception {
            insertCalls++;
            if (insertFailure != null) {
                throw insertFailure;
            }
            return nextStreamId++;
        }

        @Override
        public void finalizeRecord(int streamId, int userId)
                throws Exception {
            finalizeCalls++;
            if (finalizeFailure != null) {
                throw finalizeFailure;
            }
            successfulFinalizeCalls++;
        }
    }
}
