package com.soundreminder.ui;

import com.soundreminder.model.Priority;
import com.soundreminder.model.RecurrenceRule;
import com.soundreminder.model.RecurrenceRule.Frequency;
import com.soundreminder.model.Reminder;
import com.soundreminder.model.ReminderType;
import com.soundreminder.util.NaturalLanguageParser;
import com.soundreminder.model.ParsedInput;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tab for creating and managing scheduled date/time reminders.
 * Contains DatePicker, TimePicker, message input, image attachment, and a list of active reminders.
 */
public class ScheduledReminderTab extends Tab {

    private static final Logger LOGGER = Logger.getLogger(ScheduledReminderTab.class.getName());

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
    private DatePicker datePicker;
    private ComboBox<LocalTime> timePicker;
    private TextArea messageArea;
    private Label attachedImageLabel;
    private String selectedImagePath;
    private ComboBox<Priority> priorityCombo;
    private ComboBox<String> recurrenceCombo;
    private TextField quickAddField;
    private Button attachImageButton;
    private Button addReminderButton;
    private ListView<Reminder> remindersList;

    /**
     * Creates the Scheduled Reminder tab.
     *
     * @param storageService   the storage service
     * @param soundManager     the sound manager
     * @param allReminders     shared list of all active reminders
     * @param scheduleCallback callback to schedule a new reminder
     * @param deleteCallback   callback to delete a reminder
     */
    public ScheduledReminderTab(StorageService storageService, SoundManager soundManager,
                                List<Reminder> allReminders,
                                java.util.function.Consumer<Reminder> scheduleCallback,
                                java.util.function.Consumer<String> deleteCallback) {
        super("Scheduled Reminder");
        this.storageService = storageService;
        this.soundManager = soundManager;
        this.allReminders = allReminders;
        this.scheduleCallback = scheduleCallback;
        this.deleteCallback = deleteCallback;
        this.selectedImagePath = null;

        setClosable(false);
        buildUI();
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

        // Quick Add (NLP) row
        HBox quickRow = new HBox(12);
        quickRow.setAlignment(Pos.CENTER_LEFT);
        Label quickLabel = new Label("Quick Add:");
        quickLabel.getStyleClass().add("field-label");
        quickAddField = new TextField();
        quickAddField.setPromptText("e.g. in 20 minutes meeting !high");
        quickAddField.setPrefWidth(320);
        Button quickAddButton = new Button("Parse");
        quickAddButton.getStyleClass().add("btn-secondary");
        quickAddButton.setOnAction(e -> handleQuickAdd());
        quickRow.getChildren().addAll(quickLabel, quickAddField, quickAddButton);

        // Date picker row (existing)
        // Priority / Recurrence row
        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        Label priorityLabel = new Label("Priority:");
        priorityLabel.getStyleClass().add("field-label");
        priorityCombo = new ComboBox<>();
        priorityCombo.getItems().addAll(Priority.values());
        priorityCombo.setValue(Priority.NORMAL);
        Label recurrenceLabel = new Label("Repeat:");
        recurrenceLabel.getStyleClass().add("field-label");
        recurrenceCombo = new ComboBox<>();
        recurrenceCombo.getItems().addAll("None", "Daily", "Weekly", "Monthly", "Yearly");
        recurrenceCombo.setValue("None");
        metaRow.getChildren().addAll(priorityLabel, priorityCombo, recurrenceLabel, recurrenceCombo);
        HBox dateRow = new HBox(12);
        dateRow.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label("Date:");
        dateLabel.getStyleClass().add("field-label");

        datePicker = new DatePicker(LocalDate.now());
        datePicker.getStyleClass().add("date-picker");
        datePicker.setPromptText("Select date");

        dateRow.getChildren().addAll(dateLabel, datePicker);

        // Time picker row
        HBox timeRow = new HBox(12);
        timeRow.setAlignment(Pos.CENTER_LEFT);

        Label timeLabel = new Label("Time:");
        timeLabel.getStyleClass().add("field-label");

        timePicker = createTimPicker();

        timeRow.getChildren().addAll(timeLabel, timePicker);

        // Message area
        Label messageLabel = new Label("Message:");
        messageLabel.getStyleClass().add("field-label");

        messageArea = new TextArea();
        messageArea.getStyleClass().add("message-area");
        messageArea.setPromptText("Enter your reminder message...");
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

        addReminderButton = new Button("Add Reminder");
        addReminderButton.getStyleClass().add("btn-primary");
        addReminderButton.setOnAction(e -> handleAddReminder());

        buttonRow.getChildren().addAll(attachImageButton, addReminderButton);

        inputSection.getChildren().addAll(quickRow, dateRow, timeRow, messageLabel, messageArea,
                metaRow, attachedImageLabel, buttonRow);

        // --- Active Reminders List ---
        Label listLabel = new Label("Active Scheduled Reminders:");
        listLabel.getStyleClass().add("section-label");

        remindersList = new ListView<>();
        remindersList.getStyleClass().add("reminders-list");
        remindersList.setCellFactory(this::createReminderCell);
        remindersList.setPrefHeight(200);

        root.getChildren().addAll(inputSection, listLabel, remindersList);
        VBox.setVgrow(remindersList, javafx.scene.layout.Priority.ALWAYS);

        setContent(root);
        refreshList();
    }

    /**
     * Creates a TimePicker using a ComboBox with 5-minute intervals.
     */
    private ComboBox<LocalTime> createTimPicker() {
        ComboBox<LocalTime> combo = new ComboBox<>();
        combo.getStyleClass().add("time-picker");

        // Populate with times in 5-minute increments
        List<LocalTime> times = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            for (int minute = 0; minute < 60; minute += 5) {
                times.add(LocalTime.of(hour, minute));
            }
        }
        combo.getItems().addAll(times);
        combo.setValue(LocalTime.now().withMinute(LocalTime.now().getMinute() / 5 * 5).withSecond(0));

        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalTime time) {
                return time == null ? "" : time.format(TIME_FORMATTER);
            }

            @Override
            public LocalTime fromString(String string) {
                try {
                    return LocalTime.parse(string, TIME_FORMATTER);
                } catch (Exception e) {
                    return LocalTime.now();
                }
            }
        });

        return combo;
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

        File selected = fileChooser.showOpenDialog(remindersList.getScene().getWindow());
        if (selected != null && selected.exists()) {
            try {
                selectedImagePath = storageService.copyImageToStorage(selected.toPath());
                attachedImageLabel.setText("Attached: " + selected.getName());
                LOGGER.info("Image attached: " + selectedImagePath);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to attach image", e);
                showAlert("Error", "Failed to attach image: " + e.getMessage());
            }
        }
    }

    /**
     * Handles the "Add Reminder" button click.
     */
    private void handleAddReminder() {
        String message = messageArea.getText().trim();
        if (message.isEmpty()) {
            showAlert("Validation Error", "Please enter a reminder message.");
            return;
        }

        LocalDate date = datePicker.getValue();
        LocalTime time = timePicker.getValue();

        if (date == null) {
            showAlert("Validation Error", "Please select a date.");
            return;
        }
        if (time == null) {
            showAlert("Validation Error", "Please select a time.");
            return;
        }

        LocalDateTime triggerTime = LocalDateTime.of(date, time);

        // Check if the time is in the past
        if (triggerTime.isBefore(LocalDateTime.now())) {
            showAlert("Validation Error", "The selected date and time is in the past. Please choose a future time.");
            return;
        }

        Priority priority = priorityCombo.getValue() != null ? priorityCombo.getValue() : Priority.NORMAL;
        RecurrenceRule recurrence = buildRecurrenceRule(recurrenceCombo.getValue());

        Reminder reminder = new Reminder(
                ReminderType.SCHEDULED,
                triggerTime,
                message,
                selectedImagePath,
                recurrence,
                priority,
                new java.util.HashSet<>()
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
        datePicker.setValue(LocalDate.now());
        timePicker.setValue(LocalTime.now().withMinute(LocalTime.now().getMinute() / 5 * 5).withSecond(0));
        priorityCombo.setValue(Priority.NORMAL);
        recurrenceCombo.setValue("None");

        refreshList();
        LOGGER.info("New scheduled reminder added: " + reminder.getId());
    }

    /**
     * Creates a custom ListCell for rendering reminders with message, time, image thumbnail, and delete button.
     */
    private ListCell<Reminder> createReminderCell(ListView<Reminder> listView) {
        return new ListCell<>() {
            private final HBox content = new HBox(10);
            private final VBox textContent = new VBox(4);
            private final Label timeLabel = new Label();
            private final Label messagePreview = new Label();
            private final ImageView thumbnail = new ImageView();
            private final Button deleteButton = new Button("Delete");
            private final Region spacer = new Region();

            {
                // Style components
                timeLabel.getStyleClass().add("reminder-time-label");
                messagePreview.getStyleClass().add("reminder-message-label");
                messagePreview.setWrapText(true);
                messagePreview.setMaxWidth(300);

                thumbnail.setFitWidth(40);
                thumbnail.setFitHeight(40);
                thumbnail.setPreserveRatio(true);
                thumbnail.setSmooth(true);
                thumbnail.setVisible(false);

                deleteButton.getStyleClass().add("btn-delete");
                deleteButton.setOnAction(e -> {
                    Reminder item = getItem();
                    if (item != null) {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                                "Delete reminder: " + truncate(item.getMessage(), 30) + "?",
                                ButtonType.YES, ButtonType.NO);
                        confirm.setTitle("Confirm Delete");
                        confirm.setHeaderText(null);
                        Optional<ButtonType> result = confirm.showAndWait();
                        if (result.isPresent() && result.get() == ButtonType.YES) {
                            deleteCallback.accept(item.getId());
                            allReminders.removeIf(r -> r.getId().equals(item.getId()));
                            storageService.saveReminders(allReminders);
                            refreshList();
                        }
                    }
                });

                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                content.setAlignment(Pos.CENTER_LEFT);
                content.setPadding(new Insets(8));
                content.getChildren().addAll(thumbnail, textContent, spacer, deleteButton);
                textContent.getChildren().addAll(timeLabel, messagePreview);
            }

            @Override
            protected void updateItem(Reminder reminder, boolean empty) {
                super.updateItem(reminder, empty);

                if (empty || reminder == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    timeLabel.setText(reminder.getTriggerTime().format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    messagePreview.setText(truncate(reminder.getMessage(), 60));

                    // Load thumbnail if image exists
                    if (reminder.getImagePath() != null && !reminder.getImagePath().isBlank()) {
                        try {
                            Path resolved = storageService.resolveImagePath(reminder.getImagePath());
                            if (resolved != null && resolved.toFile().exists()) {
                                Image img = new Image(resolved.toUri().toString(), 40, 40,
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
     * Builds a RecurrenceRule from a combo box value.
     */
    private RecurrenceRule buildRecurrenceRule(String value) {
        if (value == null || value.equals("None")) return null;
        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency(Frequency.valueOf(value.toUpperCase()));
        rule.setInterval(1);
        rule.setDescription(value.toLowerCase());
        return rule;
    }

    /**
     * Parses free text into the form fields using NaturalLanguageParser.
     */
    private void handleQuickAdd() {
        String text = quickAddField.getText();
        if (text == null || text.isBlank()) {
            showAlert("Validation Error", "Please enter text to parse.");
            return;
        }
        ParsedInput parsed = new NaturalLanguageParser().parse(text);
        if (parsed.hasError()) {
            showAlert("Parse Error", parsed.getError());
            return;
        }
        if (parsed.isCountdown()) {
            LocalDateTime trigger = LocalDateTime.now().plusSeconds(parsed.getCountdownSeconds());
            datePicker.setValue(trigger.toLocalDate());
            timePicker.setValue(trigger.toLocalTime().withSecond(0).withNano(0));
        } else if (parsed.getTriggerDateTime() != null) {
            LocalDateTime trigger = parsed.getTriggerDateTime();
            datePicker.setValue(trigger.toLocalDate());
            timePicker.setValue(trigger.toLocalTime().withSecond(0).withNano(0));
        }
        if (parsed.getMessage() != null && !parsed.getMessage().isBlank()) {
            messageArea.setText(parsed.getMessage());
        }
        if (parsed.getPriority() != null) {
            priorityCombo.setValue(parsed.getPriority());
        }
        if (parsed.getRecurrenceRule() != null) {
            String name = parsed.getRecurrenceRule().getFrequency().name();
            name = name.charAt(0) + name.substring(1).toLowerCase();
            recurrenceCombo.setValue(name);
        }
    }
    public void refreshList() {
        List<Reminder> scheduled = allReminders.stream()
                .filter(r -> r.getType() == ReminderType.SCHEDULED && r.isActive())
                .sorted()
                .toList();
        remindersList.getItems().setAll(scheduled);
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
}
