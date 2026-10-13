package com.streamlink.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class VideoStreamReceiver {

    private static final String OK_PREFIX = "STATUS:OK SIZE:";
    private static final String ERROR_PREFIX = "STATUS:ERROR MESSAGE:";
    private static final int BUFFER_SIZE = 8192;

    private VideoStreamReceiver() {
    }

    static File receive(InputStream input) throws IOException {
        String status = readProtocolLine(input);
        if (status == null) {
            throw new IOException("Server closed the connection before sending a status.");
        }
        if (status.startsWith(ERROR_PREFIX)) {
            throw new IOException(status.substring(ERROR_PREFIX.length()));
        }
        if (!status.startsWith(OK_PREFIX)) {
            throw new IOException("Invalid stream response: " + status);
        }

        long advertisedSize;
        try {
            advertisedSize = Long.parseLong(status.substring(OK_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid stream size: " + status, e);
        }
        if (advertisedSize < 0) {
            throw new IOException("Server advertised a negative video size.");
        }

        File tempVideo = File.createTempFile("streamlink-", ".mp4");
        tempVideo.deleteOnExit();

        long bytesReceived = 0;
        try {
            try (OutputStream output = new FileOutputStream(tempVideo)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (bytesReceived < advertisedSize) {
                    int bytesToRead = (int) Math.min(
                            buffer.length, advertisedSize - bytesReceived);
                    int bytesRead = input.read(buffer, 0, bytesToRead);
                    if (bytesRead < 0) {
                        throw new IOException(
                                "Connection closed after " + bytesReceived
                                        + " of " + advertisedSize + " bytes.");
                    }
                    output.write(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;
                }
            }

            long savedSize = tempVideo.length();
            if (bytesReceived != advertisedSize || savedSize != advertisedSize) {
                throw new IOException("Incomplete video transfer: received "
                        + bytesReceived + " bytes, saved " + savedSize
                        + ", expected " + advertisedSize + ".");
            }
        } catch (IOException e) {
            if (!tempVideo.delete()) {
                System.out.println("Could not delete incomplete temporary file: "
                        + tempVideo.getAbsolutePath());
            }
            throw e;
        }

        System.out.println("Video stream received: " + bytesReceived + " bytes.");
        System.out.println("Verified temporary file size: " + tempVideo.length()
                + " bytes.");
        System.out.println("Saved temp video to: " + tempVideo.getAbsolutePath());
        return tempVideo;
    }

    static String readProtocolLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                int length = line.length();
                if (length > 0 && line.charAt(length - 1) == '\r') {
                    line.deleteCharAt(length - 1);
                }
                return line.toString();
            }
            line.append((char) value);
        }
        return line.length() == 0 ? null : line.toString();
    }
}
