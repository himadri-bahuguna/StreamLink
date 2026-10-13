package com.streamlink.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

public class FileManagerTest {

    @Test
    public void listsOnlyReadableMp4Files() throws IOException {
        File directory = Files.createTempDirectory("streamlink-videos").toFile();
        Files.writeString(new File(directory, "Movie.MP4").toPath(), "video");
        Files.writeString(new File(directory, "notes.txt").toPath(), "not video");
        new File(directory, "folder.mp4").mkdir();

        List<File> videos = new FileManager(directory).listVideoFiles();

        assertEquals(1, videos.size());
        assertEquals("Movie.MP4", videos.get(0).getName());
    }

    @Test
    public void searchesFilenamesCaseInsensitively() throws IOException {
        File directory = Files.createTempDirectory("streamlink-search").toFile();
        Files.writeString(new File(directory, "SampleVideo.mp4").toPath(), "video");
        Files.writeString(new File(directory, "trailer.mp4").toPath(), "video");

        List<File> matches =
                new FileManager(directory).searchVideoFiles("SAMPLE");

        assertEquals(1, matches.size());
        assertEquals("SampleVideo.mp4", matches.get(0).getName());
    }

    @Test
    public void rejectsPathsAndUnsupportedFiles() throws IOException {
        File directory = Files.createTempDirectory("streamlink-validation").toFile();
        FileManager fileManager = new FileManager(directory);

        assertThrows(IllegalArgumentException.class,
                () -> fileManager.getVideoFile("../outside.mp4"));
        assertThrows(IllegalArgumentException.class,
                () -> fileManager.getVideoFile("movie.avi"));
        assertThrows(IOException.class,
                () -> fileManager.getVideoFile("missing.mp4"));
        assertTrue(fileManager.listVideoFiles().isEmpty());
    }

    @Test
    public void resolvesStoredPathToSafeFilename() throws IOException {
        File directory = Files.createTempDirectory("streamlink-stored").toFile();
        Files.writeString(new File(directory, "sample.mp4").toPath(), "video");
        FileManager fileManager = new FileManager(directory);

        assertEquals("sample.mp4",
                fileManager.getFileNameFromStoredPath("sample.mp4"));
        assertEquals("sample.mp4",
                fileManager.getFileNameFromStoredPath(
                        directory.getName() + File.separator + "sample.mp4"));
    }

    @Test
    public void rejectsStoredPathOutsideVideoDirectory() throws IOException {
        File directory = Files.createTempDirectory("streamlink-outside").toFile();
        File outside = new File(directory.getParentFile(), "outside.mp4");
        Files.writeString(outside.toPath(), "outside");

        assertThrows(IllegalArgumentException.class,
                () -> new FileManager(directory)
                        .getFileNameFromStoredPath(outside.getAbsolutePath()));
    }
}
