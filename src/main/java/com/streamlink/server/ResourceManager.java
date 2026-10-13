package com.streamlink.server;

import java.util.ArrayDeque;
import java.util.Deque;

public class ResourceManager {
    private static final ResourceManager INSTANCE = new ResourceManager();
    private static final int MAX_STREAMS = 3;
    private int activeClients = 0;
    private int activeStreams = 0;
    private final ThreadLocal<Deque<StreamReservation>> legacyReservations =
            ThreadLocal.withInitial(ArrayDeque::new);

    private ResourceManager() {}
    public static ResourceManager getInstance() { return INSTANCE; }

    public synchronized void clientConnected()    { activeClients++; }
    public synchronized void clientDisconnected() { if (activeClients > 0) activeClients--; }

    public synchronized boolean tryStartStream() {
        StreamReservation reservation = tryReserveStream();
        if (reservation == null) {
            return false;
        }
        legacyReservations.get().push(reservation);
        return true;
    }

    public void endStream() {
        Deque<StreamReservation> reservations = legacyReservations.get();
        if (!reservations.isEmpty()) {
            reservations.pop().close();
        }
        if (reservations.isEmpty()) {
            legacyReservations.remove();
        }
    }

    public synchronized StreamReservation tryReserveStream() {
        if (activeStreams >= MAX_STREAMS) {
            return null;
        }
        activeStreams++;
        return new StreamReservation(this);
    }

    public synchronized int getActiveClients() { return activeClients; }
    public synchronized int getActiveStreams() { return activeStreams; }
    public int getMaxStreams() { return MAX_STREAMS; }

    private synchronized void releaseStream() {
        if (activeStreams > 0) {
            activeStreams--;
        }
    }

    public static final class StreamReservation implements AutoCloseable {
        private final ResourceManager resourceManager;
        private boolean released;

        private StreamReservation(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                resourceManager.releaseStream();
            }
        }
    }
}
