package com.streamlink.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;

import org.junit.Test;

public class VideoStreamerConcurrencyTest {

    @Test
    public void rejectedRequestDoesNotConsumeSlot() throws Exception {
        File directory = Files.createTempDirectory("streamlink-stream").toFile();
        Files.writeString(new File(directory, "video.mp4").toPath(), "video");
        FileManager fileManager = new FileManager(directory);
        VideoStreamer streamer = new VideoStreamer(fileManager);
        ResourceManager resourceManager = ResourceManager.getInstance();

        ResourceManager.StreamReservation first =
                resourceManager.tryReserveStream();
        ResourceManager.StreamReservation second =
                resourceManager.tryReserveStream();
        ResourceManager.StreamReservation third =
                resourceManager.tryReserveStream();
        try {
            StringWriter response = new StringWriter();
            streamer.streamVideo("video.mp4", new PrintWriter(response),
                    new ByteArrayOutputStream());
            assertTrue(response.toString().contains(
                    "Maximum number of active streams reached."));
            assertEquals(3, resourceManager.getActiveStreams());
        } finally {
            first.close();
            second.close();
            third.close();
        }
        assertEquals(0, resourceManager.getActiveStreams());
    }

    @Test
    public void successfulStreamReleasesSlot() throws Exception {
        File directory = Files.createTempDirectory("streamlink-success").toFile();
        byte[] content = "video-content".getBytes();
        Files.write(new File(directory, "video.mp4").toPath(), content);
        VideoStreamer streamer = new VideoStreamer(new FileManager(directory));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StringWriter response = new StringWriter();

        streamer.streamVideo("video.mp4", new PrintWriter(response), output);

        assertEquals(0, ResourceManager.getInstance().getActiveStreams());
        assertEquals(content.length, output.size());
        assertTrue(response.toString().contains("STATUS:OK SIZE:"));
    }

    @Test
    public void failedOutputReleasesSlot() throws Exception {
        File directory = Files.createTempDirectory("streamlink-failure").toFile();
        Files.writeString(new File(directory, "video.mp4").toPath(), "video");
        VideoStreamer streamer = new VideoStreamer(new FileManager(directory));

        streamer.streamVideo("video.mp4", new PrintWriter(new StringWriter()),
                new ByteArrayOutputStream() {
                    @Override
                    public void write(byte[] bytes, int offset, int length) {
                        throw new RuntimeException("client disconnected");
                    }
                });

        assertEquals(0, ResourceManager.getInstance().getActiveStreams());
    }
}
