package com.streamlink.client;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Requests one video directly from the stream server without authentication
 * or database access. This is only for exercising the TCP streaming path.
 */
public final class StreamingTestClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;
    private static final String DEFAULT_FILE = "sample.mp4";

    private StreamingTestClient() {
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port;
        String fileName = args.length > 1 ? args[1] : DEFAULT_FILE;

        try {
            port = args.length > 2
                    ? Integer.parseInt(args[2])
                    : DEFAULT_PORT;
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("port outside valid range");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid port. Use a value from 1 to 65535.");
            return;
        }

        if (fileName.isBlank()) {
            System.out.println("Video filename cannot be empty.");
            return;
        }

        System.out.println("Requesting " + fileName + " from " + host + ":"
                + port + " without login or database access.");

        try (
                Socket socket = new Socket(host, port);
                PrintWriter output = new PrintWriter(
                        new OutputStreamWriter(
                                socket.getOutputStream(),
                                StandardCharsets.UTF_8),
                        true);
                BufferedInputStream input =
                        new BufferedInputStream(socket.getInputStream())) {

            output.println("START_STREAM:" + fileName);
            File videoFile = VideoStreamReceiver.receive(input);

            System.out.println("Opening video player for: "
                    + videoFile.getAbsolutePath());
            try {
                VideoPlayer.openVideo(videoFile);
            } catch (RuntimeException e) {
                System.out.println("Video player could not be opened: "
                        + e.getMessage());
            }
        } catch (IOException e) {
            System.out.println("Streaming test failed: " + e.getMessage());
        }
    }
}
