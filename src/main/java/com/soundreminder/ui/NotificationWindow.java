package com.soundreminder.ui;

import com.soundreminder.model.Reminder;
import com.soundreminder.sound.SoundManager;
import com.soundreminder.storage.StorageService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NotificationWindow {

    private static final Logger LOGGER = Logger.getLogger(NotificationWindow.class.getName());

    private static final double WIDTH = 450;
    private static final double MAX_HEIGHT = 500;
    private static final double MAX_IMAGE_WIDTH = 380;
    private static final double MAX_IMAGE_HEIGHT = 250;

    private static final long[] SNOOZE_OPTIONS = {300, 900, 1800, 3600}; // 5m, 15m, 30m, 1h

    private final Stage stage;
    private final Label messageLabel;
    private final ImageView imageView;
    private final Button doneButton;
    private final Button stopRecurringButton;
    private final HBox snoozeBox;
    private final Consumer<Reminder> onDoneCallback;
    private final BiConsumer<Reminder, Long> onSnoozeCallback;
    private Reminder reminder;
    private final SoundManager soundManager;
    private final StorageService storageService;

    public NotificationWindow(SoundManager soundManager, StorageService storageService,
                              Consumer<Reminder> onDoneCallback,
                              BiConsumer<Reminder, Long> onSnoozeCallback) {
        this.soundManager = soundManager;
        this.storageService = storageService;
        this.onDoneCallback = onDoneCallback;
        this.onSnoozeCallback = onSnoozeCallback;

        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("notification-root");
        root.setPadding(new Insets(16));

        messageLabel = new Label();
        messageLabel.getStyleClass().add("notification-message");
        messageLabel.setWrapText(true);
        messageLabel.setFont(Font.font("Segoe UI", 16));

        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setVisible(false);
        imageView.setManaged(false);

        doneButton = new Button("Mark as Done");
        doneButton.getStyleClass().add("btn-primary");
        doneButton.setOnAction(e -> handleDone());

        stopRecurringButton = new Button("Stop Recurring");
        stopRecurringButton.getStyleClass().add("btn-delete");
        stopRecurringButton.setVisible(false);
        stopRecurringButton.setManaged(false);
        stopRecurringButton.setOnAction(e -> handleStopRecurring());

        snoozeBox = new HBox(8);
        snoozeBox.setAlignment(Pos.CENTER);
        for (long secs : SNOOZE_OPTIONS) {
            Button btn = new Button(formatSnoozeLabel(secs));
            btn.getStyleClass().add("btn-secondary");
            btn.setOnAction(e -> handleSnooze(secs));
            snoozeBox.getChildren().add(btn);
        }
        snoozeBox.setVisible(false);
        snoozeBox.setManaged(false);

        VBox contentBox = new VBox(12, messageLabel, imageView, snoozeBox, doneButton, stopRecurringButton);
        contentBox.setAlignment(Pos.CENTER);
        root.setCenter(contentBox);

        Scene scene = new Scene(root, WIDTH, 100);
        java.net.URL cssUrl = NotificationWindow.class.getResource("/css/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setScene(scene);

        LOGGER.info("NotificationWindow created");
    }

    public void show(Reminder reminder) {
        this.reminder = reminder;

        String typePrefix = reminder.getType().name().equals("COUNTDOWN") ? "[Timer] " : "[Reminder] ";
        messageLabel.setText(typePrefix + reminder.getMessage());

        if (reminder.getImagePath() != null && !reminder.getImagePath().isBlank()) {
            loadImage(reminder.getImagePath());
        } else {
            imageView.setVisible(false);
            imageView.setManaged(false);
        }

        boolean recurring = reminder.isRecurring();
        doneButton.setText(recurring ? "Acknowledge" : "Mark as Done");
        stopRecurringButton.setVisible(recurring);
        stopRecurringButton.setManaged(recurring);
        snoozeBox.setVisible(true);
        snoozeBox.setManaged(true);

        positionNotificationWindow();
        stage.show();
        stage.sizeToScene();
        double contentHeight = contentHeight();
        stage.setHeight(Math.min(contentHeight + 32, MAX_HEIGHT));

        LOGGER.info("Notification shown for: " + reminder.getMessage());
    }

    public void hide() {
        if (stage.isShowing()) {
            stage.hide();
            LOGGER.info("Notification hidden");
        }
    }

    private void handleDone() {
        LOGGER.info("Reminder done/acknowledged: " + (reminder != null ? reminder.getId() : "unknown"));
        soundManager.stopAlarm();
        if (reminder != null) {
            boolean recurring = reminder.isRecurring();
            if (!recurring) {
                reminder.markAsDone();
            }
            storageService.saveReminders(
                    storageService.loadReminders().stream()
                            .map(r -> r.getId().equals(reminder.getId()) ? reminder : r)
                            .toList()
            );
            onDoneCallback.accept(reminder);
        }
        stage.hide();
    }

    private void handleStopRecurring() {
        LOGGER.info("Recurring reminder stopped: " + (reminder != null ? reminder.getId() : "unknown"));
        soundManager.stopAlarm();
        if (reminder != null) {
            reminder.markAsDone();
            storageService.saveReminders(
                    storageService.loadReminders().stream()
                            .map(r -> r.getId().equals(reminder.getId()) ? reminder : r)
                            .toList()
            );
            onDoneCallback.accept(reminder);
        }
        stage.hide();
    }

    private void handleSnooze(long seconds) {
        if (reminder == null) return;
        LOGGER.info("Snoozing reminder " + reminder.getId() + " for " + seconds + "s");
        soundManager.stopAlarm();
        if (onSnoozeCallback != null) {
            onSnoozeCallback.accept(reminder, seconds);
        } else {
            // Fallback: mark as done if no snooze callback
            handleDone();
        }
        stage.hide();
    }

    private void loadImage(String imagePath) {
        try {
            Path resolved = storageService.resolveImagePath(imagePath);
            if (resolved == null || !resolved.toFile().exists()) {
                LOGGER.warning("Image file not found: " + imagePath);
                imageView.setVisible(false);
                imageView.setManaged(false);
                return;
            }
            Image image = new Image(resolved.toUri().toString(), MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT,
                    true, true, false);
            imageView.setImage(image);
            imageView.setVisible(true);
            imageView.setManaged(true);
            imageView.setFitWidth(image.getWidth());
            imageView.setFitHeight(image.getHeight());
            LOGGER.info("Image loaded in notification: " + resolved);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load image: " + imagePath, e);
            imageView.setVisible(false);
            imageView.setManaged(false);
        }
    }

    private void positionNotificationWindow() {
        Screen primary = Screen.getPrimary();
        if (primary != null) {
            javafx.geometry.Rectangle2D bounds = primary.getVisualBounds();
            stage.setX(bounds.getMaxX() - stage.getWidth() - 10);
            stage.setY(bounds.getMinY() + 10);
        } else {
            stage.setX(10);
            stage.setY(10);
        }
    }

    private double contentHeight() {
        double height = 60;
        if (imageView.isVisible() && imageView.getImage() != null) {
            height += imageView.getFitHeight() + 12;
        }
        height += 80;
        return Math.min(height, MAX_HEIGHT);
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    private static String formatSnoozeLabel(long seconds) {
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }
}
