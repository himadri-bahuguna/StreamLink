package com.streamlink.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class StreamClient {

    public static void main(String[] args) {

        String serverAddress = "localhost";
        int port = 5000;

        try (
            Socket socket =
                    new Socket(serverAddress, port);

            BufferedReader serverInput =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream()
                            )
                    );

            PrintWriter output =
                    new PrintWriter(
                            socket.getOutputStream(), true
                    );

            BufferedReader userInput =
                    new BufferedReader(
                            new InputStreamReader(System.in)
                    )
        ) {

            System.out.println(
                    "Connected to StreamLink Server!"
            );

            System.out.print(
                    "Enter video title to search: "
            );

            String keyword = userInput.readLine();

            output.println("SEARCH:" + keyword);

            String response;

            while ((response = serverInput.readLine()) != null) {

                System.out.println(response);

                if ("END".equals(response)) {
                    break;
                }
            }

        } catch (IOException e) {

            System.out.println(
                    "Connection failed: "
                    + e.getMessage()
            );
        }
    }
}