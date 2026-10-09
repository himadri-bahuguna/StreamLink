package com.streamlink.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.streamlink.database.DBConnection;

public class ClientHandler implements Runnable {

    private Socket clientSocket;

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

            if ("LIST_VIDEOS".equalsIgnoreCase(clientMessage)) {

                sendVideoList(output);

            } else if (clientMessage.startsWith("SEARCH_CATEGORY:")) {

                String category = clientMessage.substring(16).trim();
                searchVideosByCategory(category, output);

            } else if (clientMessage.startsWith("SEARCH:")) {

                String keyword = clientMessage.substring(7).trim();
                searchVideos(keyword, output);

            } else {

                output.println("Unknown request.");
                output.println("END");
            }

        } catch (IOException e) {

            System.out.println(
                "Client handling error: " + e.getMessage()
            );

        } finally {

            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error closing client socket.");
            }
        }
    }

    private void sendVideoList(PrintWriter output) {

        String query = """
            SELECT
                v.video_id,
                v.title,
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
                    resultSet.getInt("video_id")
                    + " | "
                    + resultSet.getString("title")
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
                        resultSet.getInt("video_id")
                        + " | "
                        + resultSet.getString("title")
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
                        resultSet.getInt("video_id")
                        + " | "
                        + resultSet.getString("title")
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
