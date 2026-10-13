package com.streamlink.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.streamlink.database.DBConnection;
import com.streamlink.database.UserDAO;

public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private final FileManager fileManager = new FileManager();
    private final SessionManager sessionManager = SessionManager.getInstance();
    private final StreamManager streamManager = StreamManager.getInstance();

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {

        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
            );
            PrintWriter output = new PrintWriter(
                clientSocket.getOutputStream(), true
            )
        ) {

            System.out.println(
                "Handling client in thread: "
                + Thread.currentThread().getName()
            );

            String clientMessage = input.readLine();

            System.out.println("Request from client: " + clientMessage);

            if (clientMessage == null) {
                return;
            }

            if (clientMessage.startsWith("LOGIN:")) {
                handleLogin(clientMessage.substring(6), output);

            } else if (clientMessage.startsWith("LOGOUT:")) {
                sessionManager.removeSession(clientMessage.substring(7).trim());
                output.println("LOGOUT:OK");

            } else if ("LIST_VIDEOS".equalsIgnoreCase(clientMessage)) {

                sendVideoList(output);

            } else if (clientMessage.startsWith("SEARCH_CATEGORY:")) {

                String category = clientMessage.substring(16).trim();
                searchVideosByCategory(category, output);

            } else if (clientMessage.startsWith("SEARCH:")) {

                String keyword = clientMessage.substring(7).trim();
                searchVideos(keyword, output);

            } else if (clientMessage.startsWith("START_STREAM:")) {

                String fileName = clientMessage.substring(13).trim();
                VideoStreamer videoStreamer = new VideoStreamer();

                OutputStream videoOutput = clientSocket.getOutputStream();
                VideoStreamer.TransferResult result =
                        videoStreamer.streamVideo(fileName, output, videoOutput);
                System.out.println("Legacy video transfer "
                        + result + " for " + fileName);

            } else if (clientMessage.startsWith("START_STREAM_AUTH:")) {

                handleAuthenticatedStart(clientMessage.substring(18), output);

            } else if (clientMessage.startsWith("STOP_STREAM:")) {

                handleStop(clientMessage.substring(12), output);

            } else {

                output.println("Unknown request.");
                output.println("END");
            }

        } catch (IOException e) {

            System.out.println(
                "Client handling error: " + e.getMessage()
            );

        } finally {

            ResourceManager.getInstance().clientDisconnected();

            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing client socket.");
            }
        }
    }

    private void handleLogin(String credentials, PrintWriter output) {
            int separator = credentials.indexOf(':');
            if (separator <= 0) {
                output.println("AUTH:ERROR MESSAGE:Invalid login request.");
                return;
            }

            String username = credentials.substring(0, separator);
            String password = credentials.substring(separator + 1);
            Integer userId = new UserDAO().authenticate(username, password);
            if (userId == null) {
                output.println("AUTH:ERROR MESSAGE:Invalid username or password.");
                return;
            }

            String token = sessionManager.createSession(userId);
            output.println("AUTH:OK TOKEN:" + token);
    }

    private void handleAuthenticatedStart(
                String request, PrintWriter output) {
            String[] parts = request.split(":", 3);
            if (parts.length != 3) {
                output.println("STATUS:ERROR MESSAGE:Invalid authenticated stream request.");
                return;
            }

            Integer userId = sessionManager.getUserId(parts[0]);
            if (userId == null) {
                output.println("STATUS:ERROR MESSAGE:Invalid or expired session.");
                return;
            }

            int videoId;
            try {
                videoId = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                output.println("STATUS:ERROR MESSAGE:Invalid video ID.");
                return;
            }

            StreamManager.ActiveStream stream = null;
            try {
                stream = streamManager.startStream(userId, videoId, parts[2]);
                VideoStreamer.TransferResult result =
                        new VideoStreamer().streamVideo(
                        parts[2], output, clientSocket.getOutputStream(),
                        stream.reservation());
                System.out.println("Authenticated video transfer " + result
                        + " for stream " + stream.streamId());
            } catch (Exception e) {
                output.println("STATUS:ERROR MESSAGE:" + e.getMessage());
            } finally {
                streamManager.finishStream(stream);
            }
    }

    private void handleStop(String request, PrintWriter output) {
            String[] parts = request.split(":", 2);
            if (parts.length != 2) {
                output.println("STOP_STREAM:ERROR MESSAGE:Invalid stop request.");
                return;
            }

            Integer userId = sessionManager.getUserId(parts[0]);
            if (userId == null) {
                output.println("STOP_STREAM:ERROR MESSAGE:Invalid or expired session.");
                return;
            }

            try {
                int streamId = Integer.parseInt(parts[1]);
                StreamManager.StopResult result =
                        streamManager.stopStream(userId, streamId);
                switch (result) {
                    case STOPPED -> output.println("STOP_STREAM:OK");
                    case TRANSFER_IN_PROGRESS -> output.println(
                            "STOP_STREAM:ERROR MESSAGE:Transfer is still active.");
                    case FINALIZATION_FAILED -> output.println(
                            "STOP_STREAM:ERROR MESSAGE:Database finalization failed.");
                    case NOT_FOUND_OR_UNAUTHORIZED -> output.println(
                            "STOP_STREAM:ERROR MESSAGE:Stream not found.");
                }
            } catch (NumberFormatException e) {
                output.println("STOP_STREAM:ERROR MESSAGE:Invalid stream ID.");
            } catch (Exception e) {
                output.println("STOP_STREAM:ERROR MESSAGE:" + e.getMessage());
        }
    }

    private void sendVideoList(PrintWriter output) {

        String query = """
            SELECT
                v.video_id,
                v.title,
                v.file_path,
                c.category_name,
                u.username
            FROM videos v
            JOIN categories c
                ON v.category_id = c.category_id
            JOIN users u
                ON v.uploaded_by = u.user_id
            """;

        try (
            Connection connection = DBConnection.getConnection();
            PreparedStatement statement =
                connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery()
        ) {

            output.println("===== VIDEO LIST =====");

            boolean found = false;

            while (resultSet.next()) {
                found = true;

                output.println(
                    "ID:"
                    + resultSet.getInt("video_id")
                        + " | TITLE:"
                    + resultSet.getString("title")
                        + " | FILE:"
                        + fileManager.getFileNameFromStoredPath(
                                resultSet.getString("file_path"))
                        + " | Category: "
                        + resultSet.getString("category_name")
                        + " | Uploaded by: "
                        + resultSet.getString("username")
                );
            }

            if (!found) {
                output.println("No videos available.");
            }

            output.println("END");

        } catch (Exception e) {
            System.out.println("Database error: " + e.getMessage());
            output.println("Database error.");
            output.println("END");
        }
    }

    private void searchVideos(String keyword, PrintWriter output) {

        String query = """
            SELECT
                v.video_id,
                v.title,
                v.description,
                v.file_path,
                c.category_name,
                u.username
            FROM videos v
            JOIN categories c
                ON v.category_id = c.category_id
            JOIN users u
                ON v.uploaded_by = u.user_id
            WHERE v.title LIKE ?
            """;

        try (
            Connection connection = DBConnection.getConnection();
            PreparedStatement statement =
                connection.prepareStatement(query)
        ) {

            statement.setString(1, "%" + keyword + "%");

            try (ResultSet resultSet = statement.executeQuery()) {

                output.println("===== SEARCH RESULTS =====");

                boolean found = false;

                while (resultSet.next()) {
                    found = true;

                    output.println(
                        "ID:"
                        + resultSet.getInt("video_id")
                        + " | TITLE:"
                        + resultSet.getString("title")
                        + " | FILE:"
                        + fileManager.getFileNameFromStoredPath(
                                resultSet.getString("file_path"))
                        + " | Category: "
                        + resultSet.getString("category_name")
                        + " | Uploaded by: "
                        + resultSet.getString("username")
                    );
                }

                if (!found) {
                    output.println("No videos found.");
                }

                output.println("END");
            }

        } catch (Exception e) {
            System.out.println("Search error: " + e.getMessage());
            output.println("Search error.");
            output.println("END");
        }
    }

    private void searchVideosByCategory(
            String category, PrintWriter output) {

        String query = """
            SELECT
                v.video_id,
                v.title,
                v.description,
                v.file_path,
                c.category_name,
                u.username
            FROM videos v
            JOIN categories c
                ON v.category_id = c.category_id
            JOIN users u
                ON v.uploaded_by = u.user_id
            WHERE c.category_name LIKE ?
            """;

        try (
            Connection connection = DBConnection.getConnection();
            PreparedStatement statement =
                connection.prepareStatement(query)
        ) {

            statement.setString(1, "%" + category + "%");

            try (ResultSet resultSet = statement.executeQuery()) {

                output.println("===== CATEGORY SEARCH RESULTS =====");

                boolean found = false;

                while (resultSet.next()) {
                    found = true;

                    output.println(
                        "ID:"
                        + resultSet.getInt("video_id")
                        + " | TITLE:"
                        + resultSet.getString("title")
                        + " | FILE:"
                        + fileManager.getFileNameFromStoredPath(
                                resultSet.getString("file_path"))
                        + " | Category: "
                        + resultSet.getString("category_name")
                        + " | Uploaded by: "
                        + resultSet.getString("username")
                    );
                }

                if (!found) {
                    output.println("No videos found in this category.");
                }

                output.println("END");
            }

        } catch (Exception e) {
            System.out.println(
                "Category search error: " + e.getMessage()
            );

            output.println("Category search error.");
            output.println("END");
        }
    }
}