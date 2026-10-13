
package com.streamlink.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FileManager {

    private static final String VIDEO_DIRECTORY = "videos";

    public File getVideoFile(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Video filename cannot be empty.");
        }

        // Accept only a filename, not a path supplied by a client.
        if (fileName.contains("/") || fileName.contains("\\")
                || fileName.equals(".") || fileName.equals("..")) {
            throw new IllegalArgumentException("Please provide a filename only.");
        }

        if (!fileName.toLowerCase().endsWith(".mp4")) {
            throw new IllegalArgumentException("Only MP4 files are supported.");
        }

        File videoFile = new File(VIDEO_DIRECTORY, fileName).getCanonicalFile();
        File videoDirectory = new File(VIDEO_DIRECTORY).getCanonicalFile();

        // Ensure the file stays inside the videos directory.
        if (!videoFile.toPath().startsWith(videoDirectory.toPath())) {
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

    public long getVideoSize(File videoFile) {
        return videoFile.length();
    }

    public BufferedInputStream openVideo(File videoFile)
            throws IOException {
        return new BufferedInputStream(new FileInputStream(videoFile));
    }
}
