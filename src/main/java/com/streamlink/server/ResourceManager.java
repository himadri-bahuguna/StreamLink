package com.streamlink.server;

public class ResourceManager {
    private static final ResourceManager INSTANCE = new ResourceManager();
    private final int maxStreams = 3;
    private int activeClients = 0;
    private int activeStreams = 0;

    private ResourceManager() {}
    public static ResourceManager getInstance() { return INSTANCE; }

    public synchronized void clientConnected()    { activeClients++; }
    public synchronized void clientDisconnected() { if (activeClients > 0) activeClients--; }

    public synchronized boolean tryStartStream() {
        if (activeStreams >= maxStreams) return false;
        activeStreams++;
        return true;
    }
    public synchronized void endStream() { if (activeStreams > 0) activeStreams--; }

    public synchronized int getActiveClients() { return activeClients; }
    public synchronized int getActiveStreams() { return activeStreams; }
    public int getMaxStreams() { return maxStreams; }
}
