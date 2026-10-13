package com.streamlink.server;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StreamManager {

    private static final StreamManager INSTANCE = new StreamManager(
            new JdbcStreamRecordStore(), new FileManager(),
            ResourceManager.getInstance());

    private final StreamRecordStore recordStore;
    private final FileManager fileManager;
    private final ResourceManager resourceManager;
    private final Map<Integer, ActiveStream> activeStreams =
            new ConcurrentHashMap<>();

    private StreamManager(
            StreamRecordStore recordStore, FileManager fileManager,
            ResourceManager resourceManager) {
        this.recordStore = recordStore;
        this.fileManager = fileManager;
        this.resourceManager = resourceManager;
    }

    public static StreamManager getInstance() {
        return INSTANCE;
    }

    StreamManager(
            StreamRecordStore recordStore, FileManager fileManager,
            ResourceManager resourceManager, boolean testOnly) {
        this(recordStore, fileManager, resourceManager);
    }

    public ActiveStream startStream(
            int userId, int videoId, String requestedFileName)
            throws Exception {
        String safeFileName = resolveVideoFileName(videoId, requestedFileName);
        ResourceManager.StreamReservation reservation =
                resourceManager.tryReserveStream();
        if (reservation == null) {
            throw new IOException("Maximum number of active streams reached.");
        }

        try {
            int streamId = recordStore.insert(userId, videoId);
            ActiveStream stream = new ActiveStream(
                    streamId, userId, reservation, recordStore);
            activeStreams.put(streamId, stream);
            return stream;
        } catch (Exception e) {
            reservation.close();
            throw e;
        }
    }

    public StopResult stopStream(int userId, int streamId) {
        ActiveStream stream = activeStreams.get(streamId);
        if (stream == null || stream.userId() != userId) {
            return StopResult.NOT_FOUND_OR_UNAUTHORIZED;
        }
        StopResult result = stream.stop();
        if (result == StopResult.STOPPED) {
            activeStreams.remove(streamId, stream);
        }
        return result;
    }

    public void finishStream(ActiveStream stream) {
        if (stream == null) {
            return;
        }
        StopResult result = stream.finishTransfer();
        if (result == StopResult.STOPPED) {
            activeStreams.remove(stream.streamId(), stream);
        }
    }

    private String resolveVideoFileName(int videoId, String requestedFileName)
            throws Exception {
        String safeFileName = fileManager.getFileNameFromStoredPath(
                recordStore.findStoredPath(videoId));
        if (!safeFileName.equals(requestedFileName)) {
            throw new IOException("Video filename does not match video ID.");
        }
        fileManager.getVideoFile(safeFileName);
        return safeFileName;
    }

    public enum StopResult {
        STOPPED,
        TRANSFER_IN_PROGRESS,
        FINALIZATION_FAILED,
        NOT_FOUND_OR_UNAUTHORIZED
    }

    public static final class ActiveStream {
        private final int streamId;
        private final int userId;
        private final ResourceManager.StreamReservation reservation;
        private final StreamRecordStore recordStore;
        private State state = State.TRANSFERRING;

        private ActiveStream(
                int streamId, int userId,
                ResourceManager.StreamReservation reservation,
                StreamRecordStore recordStore) {
            this.streamId = streamId;
            this.userId = userId;
            this.reservation = reservation;
            this.recordStore = recordStore;
        }

        public int streamId() {
            return streamId;
        }

        ResourceManager.StreamReservation reservation() {
            return reservation;
        }

        private int userId() {
            return userId;
        }

        synchronized StopResult stop() {
            if (state == State.TRANSFERRING) {
                return StopResult.TRANSFER_IN_PROGRESS;
            }
            if (state == State.FINALIZED) {
                return StopResult.STOPPED;
            }
            return finalizeRecord();
        }

        synchronized StopResult finishTransfer() {
            if (state == State.TRANSFERRING) {
                state = State.FINALIZATION_PENDING;
            }
            if (state == State.FINALIZATION_PENDING) {
                return finalizeRecord();
            }
            return StopResult.STOPPED;
        }

        private StopResult finalizeRecord() {
            try {
                recordStore.finalizeRecord(streamId, userId);
                state = State.FINALIZED;
                reservation.close();
                return StopResult.STOPPED;
            } catch (Exception e) {
                System.err.println("Could not finalize stream " + streamId
                        + ": " + e.getMessage());
                return StopResult.FINALIZATION_FAILED;
            }
        }

        enum State {
            TRANSFERRING,
            FINALIZATION_PENDING,
            FINALIZED
        }
    }
}
