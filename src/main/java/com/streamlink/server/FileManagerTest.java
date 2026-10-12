
package com.streamlink.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;

public class FileManagerTest {

    public static void main(String[] args) {
        FileManager fileManager = new FileManager();

        try {
            File video = fileManager.getVideoFile("sample.mp4");

            System.out.println("Video found: " + video.getAbsolutePath());
            System.out.println("Video size: "
                    + fileManager.getVideoSize(video) + " bytes");

            try (BufferedInputStream input = fileManager.openVideo(video)) {
                byte[] buffer = new byte[1024];
                int bytesRead = input.read(buffer);

                System.out.println("Bytes read: " + bytesRead);
                System.out.println("FileManager test passed.");
            }

        } catch (IOException | IllegalArgumentException e) {
            System.out.println("FileManager test failed: " + e.getMessage());
        }
    }
}
