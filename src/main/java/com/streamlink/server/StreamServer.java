package com.streamlink.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class StreamServer {

    public static void main(String[] args) {

        int port = 5000;

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            System.out.println("=================================");
            System.out.println("   StreamLink Server Started");
            System.out.println("=================================");
            System.out.println("Server is running on port: " + port);
            System.out.println("Waiting for client connections...");

            while (true) {

                Socket clientSocket = serverSocket.accept();

                System.out.println("\nNew client connected!");
                System.out.println(
                        "Client address: "
                                + clientSocket.getInetAddress()
                );

                ClientHandler clientHandler =
                        new ClientHandler(clientSocket);

                Thread clientThread =
                        new Thread(clientHandler);

                clientThread.start();
            }

        } catch (IOException e) {

            System.out.println(
                    "Server error: " + e.getMessage()
            );
        }
    }
}