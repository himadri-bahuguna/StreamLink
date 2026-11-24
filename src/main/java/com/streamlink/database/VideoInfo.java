package com.streamlink.database;

import java.time.LocalDateTime;


public record VideoInfo(
        int videoId,
        String title,
        String description,
        String fileName,
        String category,
        String uploader,
        LocalDateTime uploadDate,
        int views,
        boolean available,
        LocalDateTime lastWatched) {
}
