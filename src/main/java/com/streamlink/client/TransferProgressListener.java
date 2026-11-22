package com.streamlink.client;


@FunctionalInterface
public interface TransferProgressListener {
    void onProgress(long bytesReceived, long totalBytes);
}
