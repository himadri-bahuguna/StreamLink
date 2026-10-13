package com.streamlink.server;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.File;

public class VideoStreamer {

    private static final int BUFFER_SIZE = 8192;

    private final FileManager fileManager;
    private final ResourceManager resourceManager;

    public VideoStreamer() {
        this.fileManager = new FileManager();
        this.resourceManager = ResourceManager.getInstance();
    }

    public void streamVideo(
            String fileName, PrintWriter controlOutput,
            OutputStream videoOutput) {

        File videoFile;

        try {
            videoFile = fileManager.getVideoFile(fileName);
        } catch (IllegalArgumentException | IOException e) {
            controlOutput.println("STATUS:ERROR MESSAGE:" + e.getMessage());
            return;
        }

        if (!resourceManager.tryStartStream()) {
            controlOutput.println(
                "STATUS:ERROR MESSAGE:Maximum number of active streams reached.");
            return;
        }

        try (BufferedInputStream videoInput =
                fileManager.openVideo(videoFile)) {

            long videoSize = fileManager.getVideoSize(videoFile);
            controlOutput.println("STATUS:OK SIZE:" + videoSize);
            controlOutput.flush();

            byte[] buffer = new byte[BUFFER_SIZE];
            long bytesRemaining = videoSize;

            while (bytesRemaining > 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException(
                        "Video transfer was interrupted.");
                }

                int bytesToRead =
                    (int) Math.min(buffer.length, bytesRemaining);
                int bytesRead = videoInput.read(buffer, 0, bytesToRead);

                if (bytesRead == -1) {
                    throw new IOException(
                        "Video file ended before the expected size.");
                }

                videoOutput.write(buffer, 0, bytesRead);
                bytesRemaining -= bytesRead;
            }

            videoOutput.flush();

        } catch (InterruptedIOException e) {
            Thread.currentThread().interrupt();
            System.out.println("Video transfer interrupted: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Video transfer error: " + e.getMessage());
        } finally {
            resourceManager.endStream();
        }
    }
}
