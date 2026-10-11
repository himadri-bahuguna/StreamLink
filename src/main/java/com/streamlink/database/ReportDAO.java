package com.streamlink.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReportDAO {

    private final Connection conn;

    public ReportDAO(Connection conn) {
        this.conn = conn;
    }

    // One row of a report: a video and how many streams it has
    public static class VideoStreamCount {
        private final int videoId;
        private final String title;
        private final int streamCount;

        public VideoStreamCount(int videoId, String title, int streamCount) {
            this.videoId = videoId;
            this.title = title;
            this.streamCount = streamCount;
        }

        public int getVideoId() { return videoId; }
        public String getTitle() { return title; }
        public int getStreamCount() { return streamCount; }

        @Override
        public String toString() {
            return videoId + " | " + title + " | " + streamCount + " stream(s)";
        }
    }

    // Streams per video: every video that has at least one stream
    public List<VideoStreamCount> getStreamsPerVideo() throws SQLException {
        String sql =
            "SELECT v.video_id, v.title, COUNT(*) AS stream_count " +
            "FROM streams s " +
            "JOIN videos v ON s.video_id = v.video_id " +
            "GROUP BY v.video_id, v.title " +
            "ORDER BY v.video_id";
        return run(sql);
    }

    // Most watched: top 5 videos by number of streams
    public List<VideoStreamCount> getMostWatched() throws SQLException {
        String sql =
            "SELECT v.video_id, v.title, COUNT(*) AS stream_count " +
            "FROM streams s " +
            "JOIN videos v ON s.video_id = v.video_id " +
            "GROUP BY v.video_id, v.title " +
            "ORDER BY COUNT(*) DESC " +
            "LIMIT 5";
        return run(sql);
    }

    private List<VideoStreamCount> run(String sql) throws SQLException {
        List<VideoStreamCount> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new VideoStreamCount(
                    rs.getInt("video_id"),
                    rs.getString("title"),
                    rs.getInt("stream_count")));
            }
        }
        return results;
    }
}
