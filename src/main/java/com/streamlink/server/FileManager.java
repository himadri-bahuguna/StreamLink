
package com.streamlink.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FileManager {

    private static final String VIDEO_DIRECTORY = "videos";
    private final File videoDirectory;

    public FileManager() {
        this(new File(VIDEO_DIRECTORY));
    }

    public FileManager(File videoDirectory) {
        if (videoDirectory == null) {
            throw new IllegalArgumentException("Video directory cannot be null.");
        }

        try {
            this.videoDirectory = videoDirectory.getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Video directory cannot be resolved.", e);
        }
    }

    public File getVideoFile(String fileName) throws IOException {
        validateFileName(fileName);

        File videoFile = new File(videoDirectory, fileName).getCanonicalFile();
        if (!isInsideVideoDirectory(videoFile.toPath())) {
            throw new IllegalArgumentException("Invalid video path.");
        }

        if (!videoFile.exists() || !videoFile.isFile()) {
            throw new IOException("Video file not found: " + fileName);
        }

        if (!videoFile.canRead()) {
            throw new IOException("Video file cannot be read: " + fileName);
        }

        return videoFile;
    }

    public List<File> listVideoFiles() throws IOException {
        List<File> videoFiles = new ArrayList<>();
        if (!videoDirectory.exists()) {
            return videoFiles;
        }
        if (!videoDirectory.isDirectory()) {
            throw new IOException("Video path is not a directory: "
                    + videoDirectory.getAbsolutePath());
        }

        try (DirectoryStream<Path> entries =
                Files.newDirectoryStream(videoDirectory.toPath())) {
            for (Path entry : entries) {
                try {
                    if (isValidVideoPath(entry)) {
                        videoFiles.add(entry.toFile().getCanonicalFile());
                    }
                } catch (IOException | SecurityException e) {
                    // Ignore unreadable or broken entries while listing valid files.
                }
            }
        }

        videoFiles.sort(Comparator.comparing(
                File::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(File::getName));
        return videoFiles;
    }

    public List<File> searchVideoFiles(String keyword) throws IOException {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Search keyword cannot be empty.");
        }

        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        List<File> matches = new ArrayList<>();
        for (File videoFile : listVideoFiles()) {
            if (videoFile.getName().toLowerCase(Locale.ROOT)
                    .contains(normalizedKeyword)) {
                matches.add(videoFile);
            }
        }
        return matches;
    }

    public long getVideoSize(File videoFile) {
        return videoFile.length();
    }

    public BufferedInputStream openVideo(File videoFile)
            throws IOException {
        return new BufferedInputStream(new FileInputStream(videoFile));
    }

    private void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Video filename cannot be empty.");
        }
        if (fileName.contains("/") || fileName.contains("\\")
                || fileName.equals(".") || fileName.equals("..")) {
            throw new IllegalArgumentException("Please provide a filename only.");
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            throw new IllegalArgumentException("Only MP4 files are supported.");
        }
    }

    private boolean isInsideVideoDirectory(Path file) {
        return file.startsWith(videoDirectory.toPath());
    }

    private boolean isValidVideoPath(Path entry) throws IOException {
        if (!Files.isRegularFile(entry) || !Files.isReadable(entry)
                || !entry.getFileName().toString()
                        .toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            return false;
        }
        Path realEntry = entry.toRealPath();
        return isInsideVideoDirectory(realEntry);
    }
}
