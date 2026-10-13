package com.streamlink.client;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import com.streamlink.database.UserDAO;

public class StreamClient {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {

        try (Scanner scanner = new Scanner(System.in)) {

            System.out.println("===== STREAMLINK LOGIN =====");
            System.out.print("Enter username: ");
            String username = scanner.nextLine().trim();

            System.out.print("Enter password: ");
            String password = scanner.nextLine();

            UserDAO userDAO = new UserDAO();

            if (!userDAO.login(username, password)) {
                System.out.println("Login failed. Invalid username or password.");
                return;
            }

            System.out.println("Login successful! Welcome, " + username);

            System.out.println("\n===== STREAMLINK =====");
            System.out.println("1. List all videos");
            System.out.println("2. Search by title");
            System.out.println("3. Search by category");
            System.out.println("4. Start video stream");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();
            String request;

            switch (choice) {
                case "1":
                    request = "LIST_VIDEOS";
                    break;
                case "2":
                    System.out.print("Enter video title keyword: ");
                    request = "SEARCH:" + scanner.nextLine().trim();
                    break;
                case "3":
                    System.out.print("Enter category name: ");
                    request = "SEARCH_CATEGORY:" + scanner.nextLine().trim();
                    break;
                case "4":
                    System.out.print("Enter MP4 filename: ");
                    request = "START_STREAM:" + scanner.nextLine().trim();
                    break;
                default:
                    System.out.println("Invalid option.");
                    return;
            }

            communicate(request);
        } catch (Exception e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    private static void communicate(String request) {
        try (
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter output = new PrintWriter(
                        new OutputStreamWriter(
                                socket.getOutputStream(),
                                StandardCharsets.UTF_8),
                        true);
                BufferedInputStream input =
                        new BufferedInputStream(socket.getInputStream())) {

            output.println(request);

            if (request.startsWith("START_STREAM:")) {
                openReceivedVideo(VideoStreamReceiver.receive(input));
                return;
            }

            String response;
            while ((response = VideoStreamReceiver.readProtocolLine(input)) != null) {
                if ("END".equals(response)) {
                    break;
                }
                System.out.println(response);
            }
        } catch (IOException e) {
            System.out.println("Streaming request failed: " + e.getMessage());
        }
    }

    private static void openReceivedVideo(java.io.File videoFile) {
        System.out.println("Opening video player for: "
                + videoFile.getAbsolutePath());
        try {
            VideoPlayer.openVideo(videoFile);
        } catch (RuntimeException e) {
            System.out.println("Video player could not be opened: "
                    + e.getMessage());
        }
    }
}
