package com.soundreminder.ui;

import com.soundreminder.model.Reminder;
import com.soundreminder.model.ReminderType;
import com.soundreminder.sound.SoundManager;
import com.soundreminder.storage.StorageService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tab for creating and managing countdown timers.
 * Contains hour/minute/second input fields, message input, image attachment, and a list of active timers.
 */
public class CountdownTimerTab extends Tab {

    private static final Logger LOGGER = Logger.getLogger(CountdownTimerTab.class.getName());

    /** Storage service for persistence. */
    private final StorageService storageService;

    /** Sound manager. */
    private final SoundManager soundManager;

    /** Shared list of all active reminders. */
    private final List<Reminder> allReminders;

    /** Callback to schedule a new reminder. */
    private final java.util.function.Consumer<Reminder> scheduleCallback;

    /** Callback to delete a reminder. */
    private final java.util.function.Consumer<String> deleteCallback;

    // UI Components
    private Spinner<Integer> hoursSpinner;
    private Spinner<Integer> minutesSpinner;
    private Spinner<Integer> secondsSpinner;
    private TextArea messageArea;
    private Label attachedImageLabel;
    private Path selectedImagePath;
    private Button attachImageButton;
    private Button startTimerButton;
    private ListView<TimerDisplay> timersList;

    /** Executor for updating countdown timers every second. */
    private final ScheduledExecutorService timerUpdateExecutor;

    /**
     * Creates the Countdown Timer tab.
     *
     * @param storageService   the storage service
     * @param soundManager     the sound manager
     * @param allReminders     shared list of all active reminders
     * @param scheduleCallback callback to schedule a new reminder
     * @param deleteCallback   callback to delete a reminder
     */
    public CountdownTimerTab(StorageService storageService, SoundManager soundManager,
                             List<Reminder> allReminders,
                             java.util.function.Consumer<Reminder> scheduleCallback,
                             java.util.function.Consumer<String> deleteCallback) {
        super("Countdown Timer");
        this.storageService = storageService;
        this.soundManager = soundManager;
        this.allReminders = allReminders;
        this.scheduleCallback = scheduleCallback;
        this.deleteCallback = deleteCallback;
        this.selectedImagePath = null;
        this.timerUpdateExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TimerUpdateThread");
            t.setDaemon(true);
            return t;
        });

        setClosable(false);
        buildUI();
        startTimerUpdates();
    }

    /**
     * Builds the tab's user interface.
     */
    private void buildUI() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("tab-content");

        // --- Input Section ---
        VBox inputSection = new VBox(12);
        inputSection.getStyleClass().add("input-section");

        // Countdown duration row (HH:MM:SS)
        HBox durationRow = new HBox(12);
        durationRow.setAlignment(Pos.CENTER_LEFT);

        Label durationLabel = new Label("Countdown:");
        durationLabel.getStyleClass().add("field-label");

        hoursSpinner = createNumberSpinner(0, 23, 0, 3);
        minutesSpinner = createNumberSpinner(0, 59, 0, 3);
        secondsSpinner = createNumberSpinner(0, 59, 0, 3);

        Label hoursLabel = new Label("h");
        Label minutesLabel = new Label("m");
        Label secondsLabel = new Label("s");
        hoursLabel.getStyleClass().add("time-unit-label");
        minutesLabel.getStyleClass().add("time-unit-label");
        secondsLabel.getStyleClass().add("time-unit-label");

        durationRow.getChildren().addAll(durationLabel, hoursSpinner, hoursLabel,
                minutesSpinner, minutesLabel, secondsSpinner, secondsLabel);

        // Message area
        Label messageLabel = new Label("Message:");
        messageLabel.getStyleClass().add("field-label");

        messageArea = new TextArea();
        messageArea.getStyleClass().add("message-area");
        messageArea.setPromptText("Enter your timer message...");
        messageArea.setWrapText(true);
        messageArea.setPrefRowCount(3);

        // Attached image label
        attachedImageLabel = new Label("No image attached");
        attachedImageLabel.getStyleClass().add("attached-image-label");

        // Buttons row
        HBox buttonRow = new HBox(12);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        attachImageButton = new Button("Attach Image");
        attachImageButton.getStyleClass().add("btn-secondary");
        attachImageButton.setOnAction(e -> handleAttachImage());

        startTimerButton = new Button("Start Timer");
        startTimerButton.getStyleClass().add("btn-primary");
        startTimerButton.setOnAction(e -> handleStartTimer());

        buttonRow.getChildren().addAll(attachImageButton, startTimerButton);

        inputSection.getChildren().addAll(durationRow, messageLabel, messageArea,
                attachedImageLabel, buttonRow);

        // --- Active Timers List ---
        Label listLabel = new Label("Active Countdown Timers:");
        listLabel.getStyleClass().add("section-label");

        timersList = new ListView<>();
        timersList.getStyleClass().add("reminders-list");
        timersList.setCellFactory(this::createTimerCell);
        timersList.setPrefHeight(200);

        root.getChildren().addAll(inputSection, listLabel, timersList);
        VBox.setVgrow(timersList, Priority.ALWAYS);

        setContent(root);
    }

    /**
     * Creates a Spinner for integer input.
     */
    private Spinner<Integer> createNumberSpinner(int min, int max, int initialValue, int width) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initialValue);
        spinner.getStyleClass().add("timer-spinner");
        spinner.setEditable(true);
        spinner.setPrefWidth(60);

        // Style text field
        TextField editor = spinner.getEditor();
        editor.getStyleClass().add("timer-spinner-editor");

        return spinner;
    }

    /**
     * Handles the "Attach Image" button click.
     */
    private void handleAttachImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File selected = fileChooser.showOpenDialog(timersList.getScene().getWindow());
        if (selected != null && selected.exists()) {
            try {
                Path copied = storageService.copyImageToStorage(selected.toPath());
                selectedImagePath = copied;
                attachedImageLabel.setText("Attached: " + selected.getName());
                LOGGER.info("Image attached for timer: " + copied);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to attach image", e);
                showAlert("Error", "Failed to attach image: " + e.getMessage());
            }
        }
    }

    /**
     * Handles the "Start Timer" button click.
     */
    private void handleStartTimer() {
        int hours = hoursSpinner.getValue();
        int minutes = minutesSpinner.getValue();
        int seconds = secondsSpinner.getValue();

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;

        if (totalSeconds <= 0) {
            showAlert("Validation Error", "Please set a duration greater than zero.");
            return;
        }

        String message = messageArea.getText().trim();
        if (message.isEmpty()) {
            showAlert("Validation Error", "Please enter a timer message.");
            return;
        }

        // Calculate trigger time: now + duration
        LocalDateTime triggerTime = LocalDateTime.now().plusSeconds(totalSeconds);

        Reminder reminder = new Reminder(
                ReminderType.COUNTDOWN,
                triggerTime,
                message,
                selectedImagePath != null ? selectedImagePath.toString() : null
        );

        // Add to shared list
        allReminders.add(reminder);

        // Persist
        storageService.saveReminders(allReminders);

        // Schedule
        scheduleCallback.accept(reminder);

        // Reset form
        messageArea.clear();
        selectedImagePath = null;
        attachedImageLabel.setText("No image attached");
        hoursSpinner.getValueFactory().setValue(0);
        minutesSpinner.getValueFactory().setValue(5);
        secondsSpinner.getValueFactory().setValue(0);

        refreshList();
        LOGGER.info("New countdown timer started: " + reminder.getId());
    }

    /**
     * Creates a custom ListCell for rendering active countdown timers with remaining time
     * display, message preview, image thumbnail, and cancel button.
     */
    private ListCell<TimerDisplay> createTimerCell(ListView<TimerDisplay> listView) {
        return new ListCell<>() {
            private final HBox content = new HBox(10);
            private final VBox textContent = new VBox(4);
            private final Label remainingLabel = new Label();
            private final Label messagePreview = new Label();
            private final ImageView thumbnail = new ImageView();
            private final Button cancelButton = new Button("Cancel");
            private final Region spacer = new Region();

            {
                // Style components
                remainingLabel.getStyleClass().add("timer-remaining-label");
                messagePreview.getStyleClass().add("reminder-message-label");
                messagePreview.setWrapText(true);
                messagePreview.setMaxWidth(300);

                thumbnail.setFitWidth(40);
                thumbnail.setFitHeight(40);
                thumbnail.setPreserveRatio(true);
                thumbnail.setSmooth(true);
                thumbnail.setVisible(false);

                cancelButton.getStyleClass().add("btn-delete");
                cancelButton.setOnAction(e -> {
                    TimerDisplay item = getItem();
                    if (item != null) {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                                "Cancel timer: " + truncate(item.getMessage(), 30) + "?",
                                ButtonType.YES, ButtonType.NO);
                        confirm.setTitle("Confirm Cancel");
                        confirm.setHeaderText(null);
                        Optional<ButtonType> result = confirm.showAndWait();
                        if (result.isPresent() && result.get() == ButtonType.YES) {
                            deleteCallback.accept(item.getReminderId());
                            allReminders.removeIf(r -> r.getId().equals(item.getReminderId()));
                            storageService.saveReminders(allReminders);
                            refreshList();
                        }
                    }
                });

                HBox.setHgrow(spacer, Priority.ALWAYS);
                content.setAlignment(Pos.CENTER_LEFT);
                content.setPadding(new Insets(8));
                content.getChildren().addAll(thumbnail, textContent, spacer, cancelButton);
                textContent.getChildren().addAll(remainingLabel, messagePreview);
            }

            @Override
            protected void updateItem(TimerDisplay display, boolean empty) {
                super.updateItem(display, empty);

                if (empty || display == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    remainingLabel.setText(display.getRemainingText());
                    messagePreview.setText(truncate(display.getMessage(), 60));

                    // Load thumbnail if image exists
                    if (display.getImagePath() != null && !display.getImagePath().isBlank()) {
                        try {
                            File imgFile = new File(display.getImagePath());
                            if (imgFile.exists()) {
                                Image img = new Image(imgFile.toURI().toString(), 40, 40,
                                        true, true);
                                thumbnail.setImage(img);
                                thumbnail.setVisible(true);
                            } else {
                                thumbnail.setVisible(false);
                            }
                        } catch (Exception ex) {
                            thumbnail.setVisible(false);
                        }
                    } else {
                        thumbnail.setVisible(false);
                    }

                    setGraphic(content);
                }
            }
        };
    }

    /**
     * Starts a recurring task that updates the remaining time display for all active countdown timers.
     */
    private void startTimerUpdates() {
        timerUpdateExecutor.scheduleAtFixedRate(() -> {
            try {
                refreshList();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error updating timer list", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Refreshes the list view with current active countdown timers and calculates remaining time.
     */
    public void refreshList() {
        javafx.application.Platform.runLater(() -> {
            List<TimerDisplay> displays = allReminders.stream()
                    .filter(r -> r.getType() == ReminderType.COUNTDOWN && r.isActive())
                    .map(r -> {
                        long remainingSeconds = java.time.Duration.between(
                                LocalDateTime.now(), r.getTriggerTime()).getSeconds();
                        String remainingText = formatRemainingTime(remainingSeconds);
                        return new TimerDisplay(r.getId(), r.getMessage(), r.getImagePath(),
                                remainingText);
                    })
                    .toList();
            timersList.getItems().setAll(displays);
        });
    }

    /**
     * Formats remaining seconds into a human-readable string.
     */
    private String formatRemainingTime(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "Firing...";
        }

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        return String.format("%02d:%02d:%02d remaining", hours, minutes, seconds);
    }

    /**
     * Shows an error alert dialog.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /**
     * Truncates a string to the specified length and appends "..." if truncated.
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }

    /**
     * Simple display object for a countdown timer in the list view.
     */
    private static class TimerDisplay {
        private final String reminderId;
        private final String message;
        private final String imagePath;
        private final String remainingText;

        TimerDisplay(String reminderId, String message, String imagePath, String remainingText) {
            this.reminderId = reminderId;
            this.message = message;
            this.imagePath = imagePath;
            this.remainingText = remainingText;
        }

        public String getReminderId() {
            return reminderId;
        }

        public String getMessage() {
            return message;
        }

        public String getImagePath() {
            return imagePath;
        }

        public String getRemainingText() {
            return remainingText;
        }
    }

    /**
     * Shuts down the timer update executor.
     */
    public void shutdown() {
        timerUpdateExecutor.shutdownNow();
    }
}
