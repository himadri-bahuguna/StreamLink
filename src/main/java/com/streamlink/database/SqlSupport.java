package com.streamlink.database;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;


final class SqlSupport {

    
    static final char LIKE_ESCAPE = '!';

    private SqlSupport() {
    }

    
    static String containsPattern(String userText) {
        StringBuilder escaped = new StringBuilder("%");
        for (char c : userText.toCharArray()) {
            if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.append('%').toString();
    }

    static LocalDateTime dateTime(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    
    static Resolved resolveFile(String storedPath, StoredPathResolver resolver) {
        if (resolver != null) {
            try {
                return new Resolved(resolver.resolve(storedPath), true);
            } catch (Exception e) {
                
            }
        }
        try {
            if (storedPath == null || storedPath.isBlank() || storedPath.contains("..")) {
                return new Resolved("", false);
            }
            Path fileName = Path.of(storedPath.replace('\\', '/')).getFileName();
            return new Resolved(fileName == null ? "" : fileName.toString(), false);
        } catch (RuntimeException e) {
            return new Resolved("", false);
        }
    }

    record Resolved(String fileName, boolean available) {
    }
}
