package com.streamlink.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.Test;

public class DatabaseInitializerTest {

    @Test
    public void validatesExpectedVideoCatalogue() {
        assertEquals(
                java.util.List.of(
                        "sample.mp4",
                        "java_tutorial.mp4",
                        "music_video.mp4",
                        "football.mp4"),
                DatabaseInitializer.REQUIRED_VIDEO_FILES);
    }

    @Test
    public void rejectsTraversalAttemptsAndMissingFiles() throws IOException {
        Path directory = Files.createTempDirectory("streamlink-db-init").resolve("videos");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("safe.mp4"), "video");

        assertThrows(IllegalArgumentException.class,
                () -> DatabaseInitializer.validateStoredVideoPath(
                        "../outside.mp4", directory));
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseInitializer.validateStoredVideoPath(
                        "videos/../outside.mp4", directory));
        assertThrows(IOException.class,
                () -> DatabaseInitializer.validateStoredVideoPath(
                        "missing.mp4", directory));
        assertEquals("safe.mp4",
                DatabaseInitializer.validateStoredVideoPath("safe.mp4", directory));
    }

    @Test
    public void acceptsLegacyVideosPrefixAndDetectsInvalidFileType() throws IOException {
        Path directory = Files.createTempDirectory("streamlink-db-prefix").resolve("videos");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("legacy.mp4"), "video");
        Files.writeString(directory.resolve("text.txt"), "not video");

        assertEquals("legacy.mp4",
                DatabaseInitializer.validateStoredVideoPath("videos/legacy.mp4", directory));
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseInitializer.validateStoredVideoPath("text.txt", directory));
    }

    @Test
    public void describesSqlExceptionWithoutCredentials() {
        SQLException primary = new SQLException(
                "Access denied for user 'root'@'localhost'",
                "28000", 1045);
        primary.setNextException(new SQLException(
                "Communications link failure", "08S01", 0));

        String description =
                DatabaseInitializer.describeSQLException(primary);

        assertTrue(description.contains("Access denied"));
        assertTrue(description.contains("Communications link failure"));
        assertTrue(description.contains("SQLState=28000"));
        assertTrue(description.contains("vendorCode=1045"));
        assertTrue(!description.contains("STREAMLINK_DB_PASSWORD"));
    }
}
