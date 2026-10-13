package com.streamlink.client;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import com.streamlink.server.FileManager;

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
        if (args.length > 0 && ("--list".equalsIgnoreCase(args[0])
                || "--search".equalsIgnoreCase(args[0])
                || "--menu".equalsIgnoreCase(args[0]))) {
            runLocalVideoTools(args);
            return;
        }

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

    private static void runLocalVideoTools(String[] args) {
        FileManager fileManager = new FileManager();

        try {
            if ("--list".equalsIgnoreCase(args[0])) {
                printVideoFiles(fileManager.listVideoFiles());
                return;
            }
            if ("--search".equalsIgnoreCase(args[0])) {
                if (args.length < 2) {
                    System.out.println("Usage: --search <filename keyword>");
                    return;
                }
                printVideoFiles(fileManager.searchVideoFiles(args[1]));
                return;
            }

            runLocalMenu(fileManager);
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Local video operation failed: " + e.getMessage());
        }
    }

    private static void runLocalMenu(FileManager fileManager)
            throws IOException {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("===== LOCAL VIDEO TEST =====");
            System.out.println("1. List MP4 files");
            System.out.println("2. Search filenames");
            System.out.print("Choose an option: ");
            String choice = scanner.nextLine().trim();

            if ("1".equals(choice)) {
                printVideoFiles(fileManager.listVideoFiles());
            } else if ("2".equals(choice)) {
                System.out.print("Enter filename keyword: ");
                printVideoFiles(fileManager.searchVideoFiles(
                        scanner.nextLine().trim()));
            } else {
                System.out.println("Invalid option.");
            }
        }
    }

    private static void printVideoFiles(List<File> videoFiles) {
        if (videoFiles.isEmpty()) {
            System.out.println("No MP4 videos found.");
            return;
        }

        System.out.println("Available MP4 videos:");
        for (File videoFile : videoFiles) {
            System.out.println("- " + videoFile.getName());
        }
    }
}
