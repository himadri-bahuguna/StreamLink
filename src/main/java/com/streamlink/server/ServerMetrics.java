package com.streamlink.server;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;


public final class ServerMetrics {

    private static final ServerMetrics INSTANCE = new ServerMetrics();

    private final AtomicLong acceptedConnections = new AtomicLong();
    private final AtomicLong rejectedConnections = new AtomicLong();
    private final long startedAtMillis = System.currentTimeMillis();
    private volatile ThreadPoolExecutor workerPool;

    private ServerMetrics() {
    }

    public static ServerMetrics getInstance() {
        return INSTANCE;
    }

    void attachPool(ThreadPoolExecutor pool) {
        this.workerPool = pool;
    }

    void connectionAccepted() {
        acceptedConnections.incrementAndGet();
    }

    void connectionRejected() {
        rejectedConnections.incrementAndGet();
    }

    public long acceptedConnections() {
        return acceptedConnections.get();
    }

    public long rejectedConnections() {
        return rejectedConnections.get();
    }

    public long uptimeSeconds() {
        return (System.currentTimeMillis() - startedAtMillis) / 1000;
    }

    public int busyWorkers() {
        ThreadPoolExecutor pool = workerPool;
        return pool == null ? 0 : pool.getActiveCount();
    }

    public int maxWorkers() {
        ThreadPoolExecutor pool = workerPool;
        return pool == null ? 0 : pool.getMaximumPoolSize();
    }

    public int queuedConnections() {
        ThreadPoolExecutor pool = workerPool;
        return pool == null ? 0 : pool.getQueue().size();
    }
}
