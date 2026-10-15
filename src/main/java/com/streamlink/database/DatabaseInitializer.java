package com.streamlink.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DatabaseInitializer {

    public static final List<String> REQUIRED_CATEGORIES = List.of(
            "Movies", "Education", "Music", "Sports");

    public static final List<String> REQUIRED_VIDEO_FILES = List.of(
            "sample.mp4", "java_tutorial.mp4",
            "music_video.mp4", "football.mp4");

    private static final Path PROJECT_ROOT =
            Path.of("").toAbsolutePath().normalize();
    private static final Path VIDEO_DIRECTORY =
            PROJECT_ROOT.resolve("videos").normalize();

    private DatabaseInitializer() {
    }

    public static void verifyDatabase(Connection connection)
            throws SQLException, IOException {
        requireTable(connection, "users");
        requireTable(connection, "categories");
        requireTable(connection, "videos");
        requireTable(connection, "streams");

        requireColumns(connection, "users", List.of(
                "user_id", "username", "password"));
        requireColumns(connection, "categories", List.of(
                "category_id", "category_name"));
        requireColumns(connection, "videos", List.of(
                "video_id", "title", "description",
                "file_path", "category_id", "uploaded_by"));
        requireColumns(connection, "streams", List.of(
                "stream_id", "user_id", "video_id",
                "start_time", "end_time"));

        requireSeedCategories(connection);
        requireSeedVideos(connection);
        validateVideoFiles(VIDEO_DIRECTORY);
    }

    public static void verifyDatabase() throws SQLException, IOException {
        try (Connection connection = DBConnection.getConnection()) {
            verifyDatabase(connection);
        }
    }

    public static String validateStoredVideoPath(
            String storedPath, Path videosDirectory) throws IOException {
        if (storedPath == null || storedPath.isBlank()) {
            throw new IllegalArgumentException(
                    "Stored video path cannot be empty.");
        }

        String normalizedPath = storedPath.replace('\\', '/').trim();
        if (normalizedPath.startsWith("/")
                || normalizedPath.contains("..")) {
            throw new IllegalArgumentException(
                    "Stored video path must stay inside the videos directory.");
        }

        Path root = videosDirectory.toAbsolutePath().normalize();
        Path candidate;
        if (normalizedPath.startsWith("videos/")) {
            candidate = root.getParent().resolve(normalizedPath).normalize();
        } else {
            candidate = root.resolve(normalizedPath).normalize();
        }

        Path absoluteCandidate = candidate.toAbsolutePath().normalize();
        if (!absoluteCandidate.startsWith(root)) {
            throw new IllegalArgumentException(
                    "Stored video path resolves outside the videos directory.");
        }

        if (!Files.isRegularFile(absoluteCandidate)) {
            throw new IOException(
                    "Video file not found: " + storedPath + " in "
                            + root);
        }

        String fileName = absoluteCandidate.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            throw new IllegalArgumentException(
                    "Only MP4 files are supported.");
        }
        return fileName;
    }

    public static List<String> validateVideoFiles(Path videosDirectory)
            throws IOException {
        List<String> missing = new ArrayList<>();

        for (String videoFile : REQUIRED_VIDEO_FILES) {
            try {
                validateStoredVideoPath(videoFile, videosDirectory);
            } catch (IllegalArgumentException | IOException e) {
                missing.add(videoFile + " (" + e.getMessage() + ")");
            }
        }

        if (!missing.isEmpty()) {
            throw new IOException(
                    "Missing or invalid MP4 files in "
                            + videosDirectory.toAbsolutePath() + ": "
                            + missing);
        }

        return new ArrayList<>(REQUIRED_VIDEO_FILES);
    }

    public static void main(String[] args) {
        try {
            try (Connection connection = DBConnection.getConnection()) {
                verifyDatabase(connection);
            }
            System.out.println("Database initialization checks passed.");
        } catch (SQLException e) {
            System.err.println(
                    "Database connection or verification failed: "
                            + describeSQLException(e));
            e.printStackTrace(System.err);
            System.exit(1);
        } catch (IOException e) {
            System.err.println(
                    "Database verification failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static String describeSQLException(SQLException exception) {
        StringBuilder description = new StringBuilder();
        SQLException current = exception;
        while (current != null) {
            if (description.length() > 0) {
                description.append(" | ");
            }
            description.append(current.getClass().getSimpleName())
                    .append(": ")
                    .append(current.getMessage())
                    .append(" [SQLState=")
                    .append(current.getSQLState())
                    .append(", vendorCode=")
                    .append(current.getErrorCode())
                    .append("]");
            current = current.getNextException();
        }
        return description.toString();
    }

    private static void requireTable(Connection connection, String tableName)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(
                null, null, tableName, new String[] { "TABLE" })) {
            if (!tables.next()) {
                throw new SQLException("Required table is missing: " + tableName);
            }
        }
    }

    private static void requireColumns(
            Connection connection, String tableName, List<String> columns)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getColumns(
                null, null, tableName, null)) {
            List<String> available = new ArrayList<>();
            while (resultSet.next()) {
                available.add(resultSet.getString("COLUMN_NAME"));
            }

            for (String column : columns) {
                if (!available.contains(column)) {
                    throw new SQLException(
                            "Table " + tableName + " is missing column: "
                                    + column);
                }
            }
        }
    }

    private static void requireSeedCategories(Connection connection)
            throws SQLException {
        for (String categoryName : REQUIRED_CATEGORIES) {
            String query = "SELECT COUNT(*) FROM categories WHERE category_name = ?";
            try (PreparedStatement statement =
                    connection.prepareStatement(query)) {
                statement.setString(1, categoryName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    if (resultSet.getInt(1) == 0) {
                        throw new SQLException(
                                "Missing required category: " + categoryName);
                    }
                }
            }
        }
    }

    private static void requireSeedVideos(Connection connection)
            throws SQLException {
        for (String videoFile : REQUIRED_VIDEO_FILES) {
            String query = """
                    SELECT file_path
                    FROM videos
                    WHERE file_path = ? OR file_path = ?
                    LIMIT 1
                    """;
            try (PreparedStatement statement =
                    connection.prepareStatement(query)) {
                statement.setString(1, videoFile);
                statement.setString(2, "videos/" + videoFile);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException(
                                "Missing required video record: " + videoFile);
                    }
                    try {
                        validateStoredVideoPath(
                                resultSet.getString("file_path"),
                                VIDEO_DIRECTORY);
                    } catch (IOException | IllegalArgumentException e) {
                        SQLException validationFailure = new SQLException(
                                "Invalid required video path for "
                                        + videoFile + ": " + e.getMessage());
                        validationFailure.initCause(e);
                        throw validationFailure;
                    }
                }
            }
        }
    }
}
