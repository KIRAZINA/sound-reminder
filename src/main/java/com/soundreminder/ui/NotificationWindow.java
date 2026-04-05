package com.soundreminder.ui;

import java.io.File;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.soundreminder.model.Reminder;
import com.soundreminder.sound.SoundManager;
import com.soundreminder.storage.StorageService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
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

/**
 * Notification pop-up window that appears in the top-left corner of the screen.
 * Displays the reminder message, attached image (if any), and a "Mark as Done" checkbox.
 * Stays on top until acknowledged.
 */
public class NotificationWindow {

    private static final Logger LOGGER = Logger.getLogger(NotificationWindow.class.getName());

    /** Width of the notification window in pixels. */
    private static final double WIDTH = 450;

    /** Maximum height of the notification window in pixels. */
    private static final double MAX_HEIGHT = 500;

    /** Maximum width for attached images when displayed. */
    private static final double MAX_IMAGE_WIDTH = 380;

    /** Maximum height for attached images when displayed. */
    private static final double MAX_IMAGE_HEIGHT = 250;

    /** The JavaFX stage for this notification. */
    private final Stage stage;

    /** Label displaying the reminder message. */
    private final Label messageLabel;

    /** ImageView for displaying an attached image. */
    private final ImageView imageView;

    /** Checkbox to mark the reminder as done. */
    private final CheckBox doneCheckBox;

    /** Callback invoked when the user marks the reminder as done. */
    private final Consumer<Reminder> onDoneCallback;

    /** The reminder associated with this notification. */
    private Reminder reminder;

    /** Sound manager for stopping the alarm when done. */
    private final SoundManager soundManager;

    /** Storage service for persisting the reminder state change. */
    private final StorageService storageService;

    /**
     * Creates a new NotificationWindow.
     *
     * @param soundManager    the sound manager to stop the alarm
     * @param storageService  the storage service for persistence
     * @param onDoneCallback  callback invoked when the user marks the reminder as done
     */
    public NotificationWindow(SoundManager soundManager, StorageService storageService,
                              Consumer<Reminder> onDoneCallback) {
        this.soundManager = soundManager;
        this.storageService = storageService;
        this.onDoneCallback = onDoneCallback;

        // Create undecorated stage
        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);

        // Build the scene
        BorderPane root = new BorderPane();
        root.getStyleClass().add("notification-root");
        root.setPadding(new Insets(16));

        // Message label at the top
        messageLabel = new Label();
        messageLabel.getStyleClass().add("notification-message");
        messageLabel.setWrapText(true);
        messageLabel.setFont(Font.font("Segoe UI", 16));

        // Image view (initially hidden)
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setVisible(false);
        imageView.setManaged(false);

        // "Mark as Done" checkbox at the bottom
        doneCheckBox = new CheckBox("Mark as Done");
        doneCheckBox.getStyleClass().add("notification-done-checkbox");
        doneCheckBox.setFont(Font.font("Segoe UI", 14));

        // Center the checkbox in an HBox for proper alignment
        HBox checkBoxBox = new HBox(doneCheckBox);
        checkBoxBox.setAlignment(Pos.CENTER);
        checkBoxBox.setPadding(new Insets(12, 0, 0, 0));

        // Layout: VBox containing message, image, and checkbox
        VBox contentBox = new VBox(12, messageLabel, imageView, checkBoxBox);
        contentBox.setAlignment(Pos.CENTER_LEFT);
        root.setCenter(contentBox);

        // Scene setup
        Scene scene = new Scene(root, WIDTH, 100);
        java.net.URL cssUrl = NotificationWindow.class.getResource("/css/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setScene(scene);

        // Handle checkbox state change
        doneCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && reminder != null) {
                handleDone();
            }
        });

        LOGGER.info("NotificationWindow created");
    }

    /**
     * Shows the notification for the given reminder.
     *
     * @param reminder the reminder that triggered this notification
     */
    public void show(Reminder reminder) {
        this.reminder = reminder;

        // Set message text
        String typePrefix = reminder.getType().name().equals("COUNTDOWN") ? "[Timer] " : "[Reminder] ";
        messageLabel.setText(typePrefix + reminder.getMessage());

        // Load and display attached image if present
        if (reminder.getImagePath() != null && !reminder.getImagePath().isBlank()) {
            loadImage(reminder.getImagePath());
        } else {
            imageView.setVisible(false);
            imageView.setManaged(false);
        }

        // Reset checkbox
        doneCheckBox.setSelected(false);

        // Position in top-left corner
        positionTopLeft();

        // Show the stage
        stage.show();

        // Adjust height after showing to fit content
        stage.sizeToScene();
        double contentHeight = contentHeight();
        stage.setHeight(Math.min(contentHeight + 32, MAX_HEIGHT));

        LOGGER.info("Notification shown for: " + reminder.getMessage());
    }

    /**
     * Hides the notification window without marking the reminder as done.
     */
    public void hide() {
        if (stage.isShowing()) {
            stage.hide();
            LOGGER.info("Notification hidden");
        }
    }

    /**
     * Handles the user checking the "Mark as Done" checkbox.
     * Stops the sound, updates the reminder state, and closes the notification.
     */
    private void handleDone() {
        LOGGER.info("Reminder marked as done: " + (reminder != null ? reminder.getId() : "unknown"));

        // Stop the alarm sound
        soundManager.stopAlarm();

        // Update reminder state
        if (reminder != null) {
            reminder.markAsDone();
            // Persist the change
            storageService.saveReminders(
                    storageService.loadReminders().stream()
                            .map(r -> r.getId().equals(reminder.getId()) ? reminder : r)
                            .toList()
            );
            // Notify the caller
            onDoneCallback.accept(reminder);
        }

        // Close the notification
        stage.hide();
    }

    /**
     * Loads an image from the given path and displays it in the notification.
     *
     * @param imagePath the path to the image file
     */
    private void loadImage(String imagePath) {
        try {
            File file = new File(imagePath);
            if (!file.exists()) {
                LOGGER.warning("Image file not found: " + imagePath);
                imageView.setVisible(false);
                imageView.setManaged(false);
                return;
            }

            Image image = new Image(file.toURI().toString(), MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT,
                    true, true, false);
            imageView.setImage(image);
            imageView.setVisible(true);
            imageView.setManaged(true);
            imageView.setFitWidth(image.getWidth());
            imageView.setFitHeight(image.getHeight());

            LOGGER.info("Image loaded in notification: " + imagePath);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load image: " + imagePath, e);
            imageView.setVisible(false);
            imageView.setManaged(false);
        }
    }

    /**
     * Positions the notification window in the top-left corner of the primary screen.
     */
    private void positionTopLeft() {
        Screen primary = Screen.getPrimary();
        if (primary != null) {
            javafx.geometry.Rectangle2D bounds = primary.getVisualBounds();
            stage.setX(bounds.getMinX() + 10);
            stage.setY(bounds.getMinY() + 10);
        } else {
            stage.setX(10);
            stage.setY(10);
        }
    }

    /**
     * Calculates the approximate content height needed for the notification.
     *
     * @return the content height in pixels
     */
    private double contentHeight() {
        double height = 60; // Message label area

        if (imageView.isVisible() && imageView.getImage() != null) {
            height += imageView.getFitHeight() + 12;
        }

        height += 40; // Checkbox area
        return Math.min(height, MAX_HEIGHT);
    }

    /**
     * Checks whether the notification is currently visible.
     *
     * @return true if the window is showing
     */
    public boolean isShowing() {
        return stage.isShowing();
    }
}
