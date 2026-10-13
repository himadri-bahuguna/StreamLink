package com.streamlink.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SessionManagerTest {

    @Test
    public void sessionTokenMapsOnlyToTheServerValidatedUser() {
        SessionManager manager = SessionManager.getInstance();

        String token = manager.createSession(42);
        try {
            assertNotEquals("42", token);
            assertEquals(Integer.valueOf(42), manager.getUserId(token));
            assertNull(manager.getUserId("client-supplied-user"));
        } finally {
            manager.removeSession(token);
        }
    }

    @Test
    public void removedSessionCannotBeUsed() {
        SessionManager manager = SessionManager.getInstance();

        String token = manager.createSession(7);
        manager.removeSession(token);

        assertNull(manager.getUserId(token));
    }
}
