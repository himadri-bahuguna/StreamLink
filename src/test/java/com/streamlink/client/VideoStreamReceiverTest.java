package com.streamlink.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;

public class VideoStreamReceiverTest {

    @Test
    public void receivesExactlyAdvertisedBytes() throws Exception {
        byte[] input = "STATUS:OK SIZE:5\nhello".getBytes(StandardCharsets.UTF_8);

        File received = VideoStreamReceiver.receive(
                new ByteArrayInputStream(input));

        try {
            assertArrayEquals(
                    "hello".getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(received.toPath()));
        } finally {
            Files.deleteIfExists(received.toPath());
        }
    }

    @Test
    public void rejectsErrorResponse() {
        ByteArrayInputStream input = input(
                "STATUS:ERROR MESSAGE:Video file not found\n");

        assertThrows(IOException.class, () -> VideoStreamReceiver.receive(input));
    }

    @Test
    public void rejectsInvalidHeader() {
        ByteArrayInputStream input = input("NOT_A_STATUS\n");

        assertThrows(IOException.class, () -> VideoStreamReceiver.receive(input));
    }

    @Test
    public void rejectsNegativeSize() {
        ByteArrayInputStream input = input("STATUS:OK SIZE:-1\n");

        assertThrows(IOException.class, () -> VideoStreamReceiver.receive(input));
    }

    @Test
    public void rejectsMalformedSize() {
        ByteArrayInputStream input = input("STATUS:OK SIZE:not-a-number\n");

        assertThrows(IOException.class, () -> VideoStreamReceiver.receive(input));
    }

    @Test
    public void rejectsTruncatedPayloadAndDeletesPartialFile()
            throws Exception {
        Path tempDirectory = Path.of(System.getProperty("java.io.tmpdir"));
        Set<String> before = temporaryReceiverFiles(tempDirectory);
        ByteArrayInputStream input = input("STATUS:OK SIZE:10\nshort");

        assertThrows(IOException.class, () -> VideoStreamReceiver.receive(input));

        Set<String> after = temporaryReceiverFiles(tempDirectory);
        after.removeAll(before);
        assertFalse("Partial temporary file was not deleted.", after.stream()
                .anyMatch(name -> name.startsWith("streamlink-")
                        && name.endsWith(".mp4")));
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Set<String> temporaryReceiverFiles(Path directory)
            throws IOException {
        Set<String> files = new HashSet<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.startsWith("streamlink-")
                            && name.endsWith(".mp4"))
                    .forEach(files::add);
        }
        return files;
    }
}
