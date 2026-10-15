package com.streamlink.client;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;

public class VideoPlayer {

    private static final String APP_TITLE = "StreamLink Video Player";
    private static boolean javafxStarted;

    private Stage stage;
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;
    private Slider seekSlider;
    private Label timeLabel;
    private Label statusLabel;
    private Button playButton;
    private Button pauseButton;
    private Button stopButton;
    private File currentFile;
    private boolean draggingSlider;

    public VideoPlayer() {
        System.out.println("VideoPlayer construction on JavaFX thread: "
                + Platform.isFxApplicationThread());
        initializeUi();
    }

    public static void openVideo(File videoFile) {
        Objects.requireNonNull(videoFile, "Video file cannot be null.");

        ensureJavaFxRuntime();
        Runnable openTask = () -> {
            try {
                System.out.println("Opening video: "
                        + videoFile.getAbsolutePath());
                VideoPlayer player = new VideoPlayer();
                player.play(videoFile);
                player.show();
            } catch (IOException | RuntimeException e) {
                System.err.println("Could not initialize video player: "
                        + e.getMessage());
            }
        };

        if (Platform.isFxApplicationThread()) {
            openTask.run();
        } else {
            Platform.runLater(openTask);
        }
    }

    public void play(File videoFile) throws IOException {
        File validatedFile = validateVideoFile(videoFile);

        if (!Platform.isFxApplicationThread()) {
            final File safeFile = validatedFile;
            Platform.runLater(() -> {
                try {
                    play(safeFile);
                } catch (IOException e) {
                    setStatus("Failed to open video: " + e.getMessage());
                }
            });
            return;
        }

        if (stage == null) {
            createStage();
        }

        disposePlayer();
        currentFile = validatedFile;

        String mediaUri = validatedFile.toURI().toString();
        System.out.println("Video path: " + validatedFile.getAbsolutePath());
        System.out.println("Video URI: " + mediaUri);

        try {
            Media media = new Media(mediaUri);
            media.setOnError(() -> logMediaError(media));
            System.out.println("Media created successfully.");

            mediaPlayer = new MediaPlayer(media);
            System.out.println("MediaPlayer created successfully.");
            mediaView.setMediaPlayer(mediaPlayer);
            setStatus("Loading video...");

            mediaPlayer.setOnReady(() -> {
                System.out.println("MediaPlayer onReady fired; status="
                        + mediaPlayer.getStatus());
                Duration totalDuration = mediaPlayer.getTotalDuration();
                double maxSeconds = !isValidDuration(totalDuration)
                        ? 0.0
                        : totalDuration.toSeconds();
                seekSlider.setMax(maxSeconds);
                seekSlider.setValue(0.0);
                updateTimeLabel(Duration.ZERO);
                setStatus("Ready to play");
                updateControlState();
            });

            mediaPlayer.statusProperty().addListener(
                    (observable, oldStatus, newStatus) -> {
                        System.out.println("MediaPlayer status changed: "
                                + oldStatus + " -> " + newStatus);
                        updateControlState();
                    });
            mediaPlayer.setOnPlaying(() -> setStatus("Playing"));
            mediaPlayer.setOnPaused(() -> setStatus("Paused"));
            mediaPlayer.setOnStopped(() -> {
                if (!draggingSlider) {
                    setStatus("Stopped");
                }
            });
            mediaPlayer.setOnEndOfMedia(() -> {
                setStatus("Playback completed");
                seekSlider.setValue(seekSlider.getMax());
                updateTimeLabel(mediaPlayer.getTotalDuration());
                mediaPlayer.stop();
                updateControlState();
            });
            mediaPlayer.setOnError(() -> {
                String message = mediaPlayer.getError() == null
                        ? "Unknown media error."
                        : mediaPlayer.getError().getMessage();
                System.err.println("MediaPlayer error: " + message);
                setStatus("Playback error: " + message);
                updateControlState();
            });

            mediaPlayer.currentTimeProperty().addListener(
                    (observable, oldValue, newValue) -> {
                        if (mediaPlayer == null || draggingSlider) {
                            return;
                        }

                        Duration totalDuration = mediaPlayer.getTotalDuration();
                        if (!isValidDuration(totalDuration)) {
                            return;
                        }

                        double totalSeconds = totalDuration.toSeconds();
                        if (totalSeconds > 0.0) {
                            seekSlider.setMax(totalSeconds);
                        }
                        seekSlider.setValue(newValue.toSeconds());
                        updateTimeLabel(newValue);
                    }
            );

            seekSlider.valueChangingProperty().addListener(
                    (observable, oldValue, newValue) -> draggingSlider = newValue);

            seekSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (mediaPlayer == null || !draggingSlider) {
                    return;
                }

                Duration totalDuration = mediaPlayer.getTotalDuration();
                if (!isValidDuration(totalDuration)) {
                    return;
                }

                mediaPlayer.seek(Duration.seconds(newValue.doubleValue()));
                updateTimeLabel(Duration.seconds(newValue.doubleValue()));
            });

            stage.setTitle(APP_TITLE + " - " + validatedFile.getName());
            updateControlState();
        } catch (RuntimeException e) {
            System.err.println("Media initialization error: "
                    + e.getMessage());
            disposePlayer();
            setStatus("Playback error: " + e.getMessage());
            updateControlState();
        }
    }

    private void logMediaError(Media media) {
        String message = media.getError() == null
                ? "Unknown media error."
                : media.getError().getMessage();
        System.err.println("Media error: " + message);
        setStatus("Playback error: " + message);
        updateControlState();
    }

    public void show() {
        ensureJavaFxRuntime();

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::show);
            return;
        }

        if (stage == null) {
            createStage();
        }

        stage.show();
        stage.toFront();
    }

    public void dispose() {
        if (Platform.isFxApplicationThread()) {
            disposePlayer();
            if (stage != null) {
                stage.close();
            }
            return;
        }

        Platform.runLater(this::dispose);
    }

    private void initializeUi() {
        ensureJavaFxRuntime();

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::initializeUi);
            return;
        }

        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);
        mediaView.setFitWidth(720);
        mediaView.setFitHeight(420);

        playButton = new Button("Play");
        pauseButton = new Button("Pause");
        stopButton = new Button("Stop");
        seekSlider = new Slider(0, 100, 0);
        seekSlider.setMaxWidth(Double.MAX_VALUE);
        timeLabel = new Label("00:00 / 00:00");
        statusLabel = new Label("No video loaded.");

        playButton.setOnAction(event -> playCurrentVideo());
        pauseButton.setOnAction(event -> pauseCurrentVideo());
        stopButton.setOnAction(event -> stopCurrentVideo());

        seekSlider.setOnMouseClicked(event -> {
            if (mediaPlayer != null && mediaPlayer.getTotalDuration() != null) {
                mediaPlayer.seek(Duration.seconds(seekSlider.getValue()));
            }
        });

        HBox controlRow = new HBox(10, playButton, pauseButton, stopButton);
        controlRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(seekSlider, Priority.ALWAYS);

        VBox bottomBox = new VBox(8);
        bottomBox.getChildren().addAll(timeLabel, seekSlider, controlRow, statusLabel);
        bottomBox.setPadding(new Insets(10, 12, 12, 12));

        BorderPane layout = new BorderPane();
        layout.setCenter(mediaView);
        layout.setBottom(bottomBox);
        layout.setPadding(new Insets(12));

        stage = new Stage();
        stage.setTitle(APP_TITLE);
        stage.setScene(new Scene(layout, 760, 520));
        stage.setOnCloseRequest(event -> disposePlayer());
        updateControlState();
    }

    private void createStage() {
        ensureJavaFxRuntime();
        if (stage != null) {
            return;
        }

        stage = new Stage();
        stage.setTitle(APP_TITLE);
        stage.setOnCloseRequest(event -> disposePlayer());

        BorderPane layout = new BorderPane();
        layout.setCenter(mediaView);
        layout.setBottom(buildControlsArea());
        layout.setPadding(new Insets(12));
        stage.setScene(new Scene(layout, 760, 520));
    }

    private VBox buildControlsArea() {
        if (seekSlider == null) {
            seekSlider = new Slider(0, 100, 0);
            seekSlider.setMaxWidth(Double.MAX_VALUE);
        }
        if (timeLabel == null) {
            timeLabel = new Label("00:00 / 00:00");
        }
        if (statusLabel == null) {
            statusLabel = new Label("No video loaded.");
        }
        if (playButton == null) {
            playButton = new Button("Play");
            playButton.setOnAction(event -> playCurrentVideo());
        }
        if (pauseButton == null) {
            pauseButton = new Button("Pause");
            pauseButton.setOnAction(event -> pauseCurrentVideo());
        }
        if (stopButton == null) {
            stopButton = new Button("Stop");
            stopButton.setOnAction(event -> stopCurrentVideo());
        }

        HBox controlRow = new HBox(10, playButton, pauseButton, stopButton);
        controlRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(seekSlider, Priority.ALWAYS);

        VBox bottomBox = new VBox(8, timeLabel, seekSlider, controlRow, statusLabel);
        bottomBox.setPadding(new Insets(10, 12, 12, 12));
        return bottomBox;
    }

    private void playCurrentVideo() {
        if (mediaPlayer == null) {
            setStatus("No video available to play.");
            return;
        }

        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            return;
        }

        if (mediaPlayer.getStatus() == MediaPlayer.Status.STOPPED) {
            mediaPlayer.seek(Duration.ZERO);
        }

        mediaPlayer.play();
        updateControlState();
    }

    private void pauseCurrentVideo() {
        if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            updateControlState();
        }
    }

    private void stopCurrentVideo() {
        if (mediaPlayer == null) {
            return;
        }

        mediaPlayer.stop();
        seekSlider.setValue(0.0);
        updateTimeLabel(Duration.ZERO);
        updateControlState();
    }

    private File validateVideoFile(File videoFile) throws IOException {
        Objects.requireNonNull(videoFile, "Video file cannot be null.");

        File validatedFile = videoFile.getCanonicalFile();

        if (!validatedFile.exists()) {
            throw new IOException("Video file does not exist: " + validatedFile.getAbsolutePath());
        }
        if (!validatedFile.isFile()) {
            throw new IOException("Video path is not a regular file: " + validatedFile.getAbsolutePath());
        }
        if (!validatedFile.canRead()) {
            throw new IOException("Video file cannot be read: " + validatedFile.getAbsolutePath());
        }
        if (!validatedFile.getName().toLowerCase().endsWith(".mp4")) {
            throw new IOException("Only MP4 files are supported: " + validatedFile.getName());
        }

        return validatedFile;
    }

    private void updateControlState() {
        boolean hasMedia = mediaPlayer != null;
        boolean playing = hasMedia && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
        boolean paused = hasMedia && mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED;
        boolean stopped = hasMedia && mediaPlayer.getStatus() == MediaPlayer.Status.STOPPED;

        playButton.setDisable(!hasMedia || playing);
        pauseButton.setDisable(!hasMedia || (!paused && !playing));
        stopButton.setDisable(!hasMedia || (!playing && !paused && !stopped));
        seekSlider.setDisable(!hasMedia);
    }

    private void updateTimeLabel(Duration currentTime) {
        Duration totalDuration = mediaPlayer == null ? Duration.ZERO : mediaPlayer.getTotalDuration();
        if (!isValidDuration(totalDuration)) {
            totalDuration = Duration.ZERO;
        }

        String currentText = formatDuration(currentTime);
        String totalText = formatDuration(totalDuration);
        timeLabel.setText(currentText + " / " + totalText);
    }

    private String formatDuration(Duration duration) {
        if (!isValidDuration(duration)) {
            return "00:00";
        }

        long totalSeconds = (long) duration.toSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private boolean isValidDuration(Duration duration) {
        return duration != null && !duration.isIndefinite() && !duration.isUnknown();
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private void disposePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }

        if (mediaView != null) {
            mediaView.setMediaPlayer(null);
        }

        if (seekSlider != null) {
            seekSlider.setValue(0.0);
        }

        if (timeLabel != null) {
            timeLabel.setText("00:00 / 00:00");
        }

        if (statusLabel != null) {
            statusLabel.setText("No video loaded.");
        }
    }

    private static void ensureJavaFxRuntime() {
        if (javafxStarted) {
            return;
        }

        try {
            Platform.startup(() -> {
            });
            javafxStarted = true;
        } catch (IllegalStateException ignored) {
            javafxStarted = true;
        }
    }
}
