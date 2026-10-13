package com.streamlink.server;

import com.streamlink.database.DBConnection;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

final class JdbcStreamRecordStore implements StreamRecordStore {

    @Override
    public String findStoredPath(int videoId) throws Exception {
        String query = "SELECT file_path FROM videos WHERE video_id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, videoId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IOException("Video not found: " + videoId);
                }
                return resultSet.getString("file_path");
            }
        }
    }

    @Override
    public int insert(int userId, int videoId) throws Exception {
        String query = """
                INSERT INTO streams (user_id, video_id, start_time)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """;
        try (Connection connection = DBConnection.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    query, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, userId);
                statement.setInt(2, videoId);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new IOException(
                                "Stream record ID was not returned.");
                    }
                    int streamId = keys.getInt(1);
                    connection.commit();
                    return streamId;
                }
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    @Override
    public void finalizeRecord(int streamId, int userId) throws Exception {
        String query = """
                UPDATE streams
                SET end_time = CURRENT_TIMESTAMP
                WHERE stream_id = ? AND user_id = ? AND end_time IS NULL
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, streamId);
            statement.setInt(2, userId);
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new IOException(
                        "Stream record was not finalized: " + streamId);
            }
        }
    }
}
