package com.streamlink.client;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.streamlink.database.UserDAO;

import javafx.application.Application;
import javafx.concurrent.Task;
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

    private ListView<VideoRecord> videoList;
    private TextField searchField;
    private ComboBox<String> categoryBox;
    private Label statusLabel;
    private Button playButton;
    private boolean playbackInProgress;

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

            if (new UserDAO().login(username, password)) {
                showVideoScreen(stage, username);
            } else {
                messageLabel.setText("Invalid username or password.");
            }
        });

        VBox layout = new VBox(
                15, title, subtitle, usernameField,
                passwordField, loginButton, messageLabel);
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
        playButton = new Button("Play");
        playButton.setDisable(true);

        categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll(
                "All Categories", "Movies", "Education", "Music", "Sports");
        categoryBox.setValue("All Categories");

        videoList = new ListView<>();
        videoList.setPrefHeight(300);
        videoList.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> updatePlayButton());

        statusLabel = new Label("Loading videos...");

        searchButton.setOnAction(event -> {
            String keyword = searchField.getText().trim();
            loadVideos(keyword.isEmpty() ? "LIST_VIDEOS" : "SEARCH:" + keyword);
        });
        refreshButton.setOnAction(event -> {
            searchField.clear();
            categoryBox.setValue("All Categories");
            loadVideos("LIST_VIDEOS");
        });
        categoryBox.setOnAction(event -> {
            String category = categoryBox.getValue();
            loadVideos(category == null || category.equals("All Categories")
                    ? "LIST_VIDEOS" : "SEARCH_CATEGORY:" + category);
        });
        playButton.setOnAction(event -> playSelectedVideo());

        HBox searchRow = new HBox(
                10, searchField, searchButton, refreshButton, playButton);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        VBox layout = new VBox(
                15, title, welcomeLabel, searchRow,
                categoryBox, videoList, statusLabel);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #f4f7fb;");
        stage.setTitle("StreamLink - Browse Videos");
        stage.setScene(new Scene(layout, 760, 520));
        stage.setResizable(true);
        stage.show();
        loadVideos("LIST_VIDEOS");
    }

    private void loadVideos(String request) {
        videoList.getItems().clear();
        statusLabel.setText("Loading...");
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter output = new PrintWriter(
                        new OutputStreamWriter(
                                socket.getOutputStream(),
                                StandardCharsets.UTF_8), true);
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream(),
                                StandardCharsets.UTF_8))) {
            output.println(request);
            String response;
            while ((response = input.readLine()) != null
                    && !"END".equals(response)) {
                if (response.startsWith("Database error.")
                        || response.startsWith("Search error.")
                        || response.startsWith("Category search error.")) {
                    throw new IOException(response);
                }
                VideoRecord record = VideoRecord.parse(response);
                if (record != null) {
                    videoList.getItems().add(record);
                }
            }
            statusLabel.setText(videoList.getItems().isEmpty()
                    ? "No videos found."
                    : "Videos found: " + videoList.getItems().size());
        } catch (Exception e) {
            statusLabel.setText("Could not load videos: " + e.getMessage());
            System.err.println("Video loading error: " + e.getMessage());
        }
        updatePlayButton();
    }

    private void playSelectedVideo() {
        VideoRecord selected = videoList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a video before clicking Play.");
            return;
        }
        if (playbackInProgress) {
            return;
        }

        playbackInProgress = true;
        updatePlayButton();
        statusLabel.setText("Downloading " + selected.fileName() + "...");

        Task<File> task = new Task<>() {
            @Override
            protected File call() throws IOException {
                return receiveVideo(selected.fileName());
            }
        };
        task.setOnSucceeded(event -> {
            playbackInProgress = false;
            File receivedFile = task.getValue();
            statusLabel.setText("Opening " + receivedFile.getName() + "...");
            VideoPlayer.openVideo(receivedFile);
            updatePlayButton();
        });
        task.setOnFailed(event -> {
            playbackInProgress = false;
            Throwable error = task.getException();
            statusLabel.setText("Playback failed: " + error.getMessage());
            System.err.println("Playback error: " + error.getMessage());
            updatePlayButton();
        });
        Thread playbackThread = new Thread(task, "streamlink-playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private File receiveVideo(String fileName) throws IOException {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter output = new PrintWriter(
                        new OutputStreamWriter(
                                socket.getOutputStream(),
                                StandardCharsets.UTF_8), true);
                BufferedInputStream input =
                        new BufferedInputStream(socket.getInputStream())) {
            output.println("START_STREAM:" + fileName);
            return VideoStreamReceiver.receive(input);
        }
    }

    private void updatePlayButton() {
        if (playButton != null) {
            playButton.setDisable(playbackInProgress
                    || videoList.getSelectionModel().getSelectedItem() == null);
        }
    }

    private static final class VideoRecord {
        private final int videoId;
        private final String title;
        private final String fileName;
        private final String displayText;

        private VideoRecord(
                int videoId, String title, String fileName, String displayText) {
            this.videoId = videoId;
            this.title = title;
            this.fileName = fileName;
            this.displayText = displayText;
        }

        private static VideoRecord parse(String response) {
            if (!response.startsWith("ID:") || !response.contains(" | TITLE:")
                    || !response.contains(" | FILE:")) {
                return null;
            }
            try {
                int idEnd = response.indexOf(" | TITLE:");
                int fileStart = response.indexOf(" | FILE:", idEnd);
                int categoryStart = response.indexOf(" | Category:", fileStart);
                int id = Integer.parseInt(response.substring(3, idEnd).trim());
                String title = response.substring(idEnd + 9, fileStart).trim();
                String fileName = response.substring(
                        fileStart + 8,
                        categoryStart < 0 ? response.length() : categoryStart)
                        .trim();
                if (fileName.isEmpty() || fileName.contains("/")
                        || fileName.contains("\\")
                        || !fileName.toLowerCase().endsWith(".mp4")) {
                    return null;
                }
                return new VideoRecord(id, title, fileName, response);
            } catch (RuntimeException e) {
                return null;
            }
        }

        private String fileName() {
            return fileName;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
