package com.streamlink.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

public class VideoStreamingIntegrationTest {

    @Test
    public void transfersCompletePayloadOverSocket() throws Exception {
        byte[] sourceBytes = "known-video-payload".getBytes(
                StandardCharsets.UTF_8);
        File directory = Files.createTempDirectory("streamlink-socket")
                .toFile();
        File source = new File(directory, "video.mp4");
        Files.write(source.toPath(), sourceBytes);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5000);
            Future<VideoStreamer.TransferResult> serverResult =
                    executor.submit(() -> serveOneRequest(server, directory));

            try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                client.setSoTimeout(5000);
                PrintWriter request = new PrintWriter(
                        new OutputStreamWriter(
                                client.getOutputStream(),
                                StandardCharsets.UTF_8),
                        true);
                BufferedInputStream input = new BufferedInputStream(
                        client.getInputStream());

                request.println("START_STREAM:video.mp4");
                String response = readProtocolLine(input);
                assertTrue(response.startsWith("STATUS:OK SIZE:"));
                long advertisedSize = Long.parseLong(
                        response.substring("STATUS:OK SIZE:".length()));

                ByteArrayOutputStream received = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                while (received.size() < advertisedSize) {
                    int bytesToRead = (int) Math.min(
                            buffer.length, advertisedSize - received.size());
                    int bytesRead = input.read(buffer, 0, bytesToRead);
                    assertTrue("Socket closed before transfer completed.",
                            bytesRead >= 0);
                    received.write(buffer, 0, bytesRead);
                }

                assertEquals(sourceBytes.length, advertisedSize);
                assertArrayEquals(sourceBytes, received.toByteArray());
            }

            assertEquals(VideoStreamer.TransferResult.SUCCESS,
                    getServerResult(serverResult));
            assertEquals(0, ResourceManager.getInstance().getActiveStreams());
        } finally {
            executor.shutdownNow();
            Files.deleteIfExists(source.toPath());
            Files.deleteIfExists(directory.toPath());
        }
    }

    private static String readProtocolLine(InputStream input)
            throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                if (line.length() > 0
                        && line.charAt(line.length() - 1) == '\r') {
                    line.deleteCharAt(line.length() - 1);
                }
                return line.toString();
            }
            line.append((char) value);
        }
        throw new IOException("Socket closed before status line.");
    }

    private static VideoStreamer.TransferResult serveOneRequest(
            ServerSocket server, File directory) throws IOException {
        try (Socket client = server.accept();
                BufferedReader request = new BufferedReader(
                        new InputStreamReader(
                                client.getInputStream(),
                                StandardCharsets.UTF_8));
                PrintWriter control = new PrintWriter(
                        new OutputStreamWriter(
                                client.getOutputStream(),
                                StandardCharsets.UTF_8),
                        true);
                OutputStream output = client.getOutputStream()) {
            assertEquals("START_STREAM:video.mp4", request.readLine());
            return new VideoStreamer(new FileManager(directory))
                    .streamVideo("video.mp4", control, output);
        }
    }

    private static VideoStreamer.TransferResult getServerResult(
            Future<VideoStreamer.TransferResult> result)
            throws InterruptedException, ExecutionException, TimeoutException {
        return result.get(5, TimeUnit.SECONDS);
    }
}
