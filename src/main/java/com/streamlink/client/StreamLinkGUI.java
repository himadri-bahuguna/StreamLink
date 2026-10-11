
package com.streamlink.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import com.streamlink.database.UserDAO;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class StreamLinkGUI extends Application {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;

    private ListView<String> videoList;
    private TextField searchField;
    private ComboBox<String> categoryBox;
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        Label title = new Label("STREAMLINK");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");

        Label subtitle = new Label("Login to your account");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter username");
        usernameField.setMaxWidth(280);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");
        passwordField.setMaxWidth(280);

        Button loginButton = new Button("LOGIN");
        loginButton.setMaxWidth(280);

        Label messageLabel = new Label();

        loginButton.setOnAction(event -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();

            if (username.isEmpty() || password.isEmpty()) {
                messageLabel.setText("Please enter both fields.");
                return;
            }

            UserDAO userDAO = new UserDAO();

            if (userDAO.login(username, password)) {
                showVideoScreen(stage, username);
            } else {
                messageLabel.setText("Invalid username or password.");
            }
        });

        VBox layout = new VBox(
                15, title, subtitle, usernameField,
                passwordField, loginButton, messageLabel
        );

        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));
        layout.setStyle("-fx-background-color: #f4f7fb;");

        stage.setTitle("StreamLink - Login");
        stage.setScene(new Scene(layout, 420, 420));
        stage.setResizable(false);
        stage.show();
    }

    private void showVideoScreen(Stage stage, String username) {
        Label title = new Label("STREAMLINK");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");

        Label welcomeLabel = new Label("Welcome, " + username + "!");

        searchField = new TextField();
        searchField.setPromptText("Search videos by title");
        searchField.setPrefWidth(240);

        Button searchButton = new Button("Search");
        Button refreshButton = new Button("Show All");

        categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll(
                "All Categories", "Movies",
                "Education", "Music", "Sports"
        );
        categoryBox.setValue("All Categories");

        videoList = new ListView<>();
        videoList.setPrefHeight(300);

        statusLabel = new Label("Loading videos...");

        searchButton.setOnAction(event -> {
            String keyword = searchField.getText().trim();

            if (keyword.isEmpty()) {
                loadVideos("LIST_VIDEOS");
            } else {
                loadVideos("SEARCH:" + keyword);
            }
        });

        refreshButton.setOnAction(event -> {
            searchField.clear();
            categoryBox.setValue("All Categories");
            loadVideos("LIST_VIDEOS");
        });

        categoryBox.setOnAction(event -> {
            String category = categoryBox.getValue();

            if (category == null || category.equals("All Categories")) {
                loadVideos("LIST_VIDEOS");
            } else {
                loadVideos("SEARCH_CATEGORY:" + category);
            }
        });

        HBox searchRow = new HBox(
                10, searchField, searchButton, refreshButton
        );
        searchRow.setAlignment(Pos.CENTER_LEFT);

        VBox layout = new VBox(
                15, title, welcomeLabel, searchRow,
                categoryBox, videoList, statusLabel
        );
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #f4f7fb;");

        stage.setTitle("StreamLink - Browse Videos");
        stage.setScene(new Scene(layout, 650, 520));
        stage.setResizable(true);
        stage.show();

        loadVideos("LIST_VIDEOS");
    }

    private void loadVideos(String request) {
        videoList.getItems().clear();
        statusLabel.setText("Loading...");

        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             PrintWriter output = new PrintWriter(
                     socket.getOutputStream(), true);
             BufferedReader input = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {

            output.println(request);

            String response;
            while ((response = input.readLine()) != null) {
                if (response.equals("END")) {
                    break;
                }

                videoList.getItems().add(response);
            }

            if (videoList.getItems().isEmpty()) {
                statusLabel.setText("No videos found.");
            } else {
                statusLabel.setText(
                        "Videos found: " + videoList.getItems().size()
                );
            }

        } catch (Exception e) {
            statusLabel.setText(
                    "Could not connect to server. Please check if it is running."
            );
            System.out.println("Video loading error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}