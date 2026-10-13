package com.streamlink.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();
    private final Map<String, Integer> sessions = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public String createSession(int userId) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, userId);
        return token;
    }

    public Integer getUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return sessions.get(token);
    }

    public void removeSession(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
