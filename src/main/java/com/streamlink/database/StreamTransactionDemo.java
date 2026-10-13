package com.streamlink.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.streamlink.server.ResourceManager;

public class StreamTransactionDemo {

    

    private static final ResourceManager rm = ResourceManager.getInstance();

    // ---- The transaction ----
    static boolean startStream(Connection conn, int userId, int videoId) {
        // 1. Reserve a slot in the counter
        if (!rm.tryStartStream()) {
            System.out.println("Stream limit reached, cannot start stream.");
            return false;
        }

        String sql = "INSERT INTO streams (user_id, video_id, start_time) VALUES (?, ?, NOW())";

        try {
            conn.setAutoCommit(false);

            // 2. INSERT into streams
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setInt(2, videoId);
                ps.executeUpdate();
            }

            // 3. Commit
            conn.commit();
            System.out.println("Stream started: user=" + userId + ", video=" + videoId);
            return true;

        } catch (SQLException e) {
            System.out.println("Insert failed: " + e.getMessage());
            try {
                conn.rollback();
                System.out.println("Rolled back.");
            } catch (SQLException re) {
                System.out.println("Rollback failed: " + re.getMessage());
            }
            // Release the slot so the counter does not leak
            rm.endStream();
            return false;

        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    public static void main(String[] args) {
        // If DBConnection has something like getConnection(), use that instead:
        // try (Connection conn = DBConnection.getConnection()) {
        try (Connection conn = DBConnection.getConnection())  {

            // Use a user_id and video_id that really exist in your tables
            int validUserId = 1;
            int validVideoId = 1;
            int badVideoId = 99999; // does not exist -> FK violation

            System.out.println("Active streams at start: " + rm.getActiveStreams());

            System.out.println("\n--- Test 1: valid insert ---");
            startStream(conn, validUserId, validVideoId);
            System.out.println("Active streams: " + rm.getActiveStreams()); // expect 1

            System.out.println("\n--- Test 2: forced failure (bad video_id) ---");
            startStream(conn, validUserId, badVideoId);
            System.out.println("Active streams: " + rm.getActiveStreams()); // expect still 1, no leak

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
