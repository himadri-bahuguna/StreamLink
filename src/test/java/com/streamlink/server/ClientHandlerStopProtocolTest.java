
package com.streamlink.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class ClientHandlerStopProtocolTest {

    @Test
    public void rejectsStopRequestWithInvalidSession() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5000);

            try (Socket client = new Socket(
                    "127.0.0.1", server.getLocalPort());
                 Socket accepted = server.accept()) {

                client.setSoTimeout(5000);

                Thread handler = new Thread(
                        new ClientHandler(accepted));
                handler.start();

                try {
                    PrintWriter output = new PrintWriter(
                            client.getOutputStream(), true);
                    BufferedReader input = new BufferedReader(
                            new InputStreamReader(
                                    client.getInputStream(),
                                    StandardCharsets.UTF_8));

                    output.println("STOP_STREAM:invalid-token:1");

                    assertEquals(
                            "STOP_STREAM:ERROR MESSAGE:Invalid or expired session.",
                            input.readLine());
                } finally {
                    handler.join(5000);
                }

                assertFalse(
                        "ClientHandler should finish handling the request.",
                        handler.isAlive());
            }
        }
    }

    @Test
    public void rejectsStopRequestForNonexistentStream() throws Exception {
        SessionManager sessions = SessionManager.getInstance();
        String token = sessions.createSession(1);

        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5000);

            try (Socket client = new Socket(
                    "127.0.0.1", server.getLocalPort());
                 Socket accepted = server.accept()) {

                client.setSoTimeout(5000);

                Thread handler = new Thread(
                        new ClientHandler(accepted));
                handler.start();

                try {
                    PrintWriter output = new PrintWriter(
                            client.getOutputStream(), true);
                    BufferedReader input = new BufferedReader(
                            new InputStreamReader(
                                    client.getInputStream(),
                                    StandardCharsets.UTF_8));

                    output.println("STOP_STREAM:" + token + ":999999");

                    assertEquals(
                            "STOP_STREAM:ERROR MESSAGE:Stream not found.",
                            input.readLine());
                } finally {
                    handler.join(5000);
                }

                assertFalse(
                        "ClientHandler should finish handling the request.",
                        handler.isAlive());
            }
        } finally {
            sessions.removeSession(token);
        }
    }
}
