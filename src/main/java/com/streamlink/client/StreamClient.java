
package com.streamlink.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

import com.streamlink.database.UserDAO;

public class StreamClient {

    public static void main(String[] args) {

        try (Scanner scanner = new Scanner(System.in)) {

            // Step 1: Login
            System.out.println("===== STREAMLINK LOGIN =====");
            System.out.print("Enter username: ");
            String username = scanner.nextLine().trim();

            System.out.print("Enter password: ");
            String password = scanner.nextLine();

            UserDAO userDAO = new UserDAO();

            if (!userDAO.login(username, password)) {
                System.out.println(
                        "Login failed. Invalid username or password."
                );
                return;
            }

            System.out.println("Login successful! Welcome, " + username);

            // Step 2: Show video menu
            System.out.println("\n===== STREAMLINK =====");
            System.out.println("1. List all videos");
            System.out.println("2. Search by title");
            System.out.println("3. Search by category");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();
            String request;

            switch (choice) {
                case "1":
                    request = "LIST_VIDEOS";
                    break;

                case "2":
                    System.out.print("Enter video title keyword: ");
                    request = "SEARCH:"
                            + scanner.nextLine().trim();
                    break;

                case "3":
                    System.out.print("Enter category name: ");
                    request = "SEARCH_CATEGORY:"
                            + scanner.nextLine().trim();
                    break;

                default:
                    System.out.println("Invalid option.");
                    return;
            }

            // Step 3: Communicate with the server
            try (
                Socket socket = new Socket("localhost", 5000);
                PrintWriter output = new PrintWriter(
                        socket.getOutputStream(), true);
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()))
            ) {

                output.println(request);

                String response;

                while ((response = input.readLine()) != null) {
                    if ("END".equals(response)) {
                        break;
                    }

                    System.out.println(response);
                }

            } catch (Exception e) {
                System.out.println(
                        "Could not communicate with the server. "
                        + "Make sure StreamServer is running."
                );
            }

        } catch (Exception e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }
}
