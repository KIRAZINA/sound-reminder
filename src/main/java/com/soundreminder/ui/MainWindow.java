package com.soundreminder.ui;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.soundreminder.model.AppSettings;
import com.soundreminder.model.Reminder;
import com.soundreminder.scheduler.ReminderScheduler;
import com.soundreminder.sound.SoundManager;
import com.soundreminder.storage.StorageService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Main application window containing the TabPane with Scheduled Reminder,
 * Countdown Timer tabs, and a toolbar with Settings button.
 * Manages the lifecycle of all sub-components: storage, sound, scheduler, notifications.
 */
public class MainWindow {

    private static final Logger LOGGER = Logger.getLogger(MainWindow.class.getName());

    /** Replay delay in seconds (60 seconds before sound stops). */
    private static final long REPLAY_DELAY_SECONDS = 60;

    /** Replay interval in seconds (5 minutes = 300 seconds from original trigger). */
    private static final long REPLAY_INTERVAL_SECONDS = 300;

    /** The main application stage. */
    private final Stage stage;

    /** Storage service for persistence. */
    private final StorageService storageService;

    /** Sound manager for alarm playback. */
    private final SoundManager soundManager;

    /** Executor service for background tasks (scheduling, replay, etc.). */
    private final ScheduledExecutorService executorService;

    /** Scheduler for precise reminder timing. */
    private final ReminderScheduler scheduler;

    /** Shared list of all active reminders (thread-safe). */
    private final CopyOnWriteArrayList<Reminder> allReminders;

    /** Currently active notification window. */
    private NotificationWindow activeNotification;

    /** Currently firing reminder (for replay logic). */
    private Reminder currentlyFiringReminder;

    /** Number of times the current reminder has been replayed. */
    private int replayCount;

    // UI Components
    private TabPane tabPane;
    private ScheduledReminderTab scheduledTab;
    private CountdownTimerTab countdownTab;
    private SystemTrayManager systemTrayManager;

    /**
     * Creates and displays the main application window.
     *
     * @param stage           the primary stage
     * @param storageService  the storage service (already initialized)
     * @param settings        the application settings (already loaded)
     */
    public MainWindow(Stage stage, StorageService storageService, AppSettings settings) {
        this.stage = stage;
        this.storageService = storageService;
        this.soundManager = new SoundManager(settings);
        this.allReminders = new CopyOnWriteArrayList<>();

        // Create executor service for background tasks
        this.executorService = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "SoundReminder-Worker");
            t.setDaemon(true);
            return t;
        });

        // Create scheduler with callback for when reminders fire
        this.scheduler = new ReminderScheduler(executorService, this::onReminderFired);

        // Build the UI
        buildMainWindow();

        // Load saved reminders
        loadSavedReminders();

        // Show the main window
        configureStage();
        stage.show();

        // Initialize system tray (desktop only)
        systemTrayManager = new SystemTrayManager(stage, () ->
                allReminders.stream().filter(Reminder::isActive).toList()
        );

        LOGGER.info("Main window initialized");
    }

    /**
     * Builds the main window layout with TabPane and toolbar.
     */
    private void buildMainWindow() {
        BorderPane root = new BorderPane();

        // --- Toolbar ---
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(8, 16, 8, 16));
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.getStyleClass().add("main-toolbar");

        Button settingsButton = new Button("Settings");
        settingsButton.getStyleClass().add("btn-secondary");
        settingsButton.setOnAction(e -> openSettings());

        Label statusLabel = new Label("SoundReminder v1.0");
        statusLabel.getStyleClass().add("status-label");
        HBox.setHgrow(statusLabel, javafx.scene.layout.Priority.ALWAYS);

        toolbar.getChildren().addAll(statusLabel, settingsButton);
        root.setTop(toolbar);

        // --- TabPane ---
        tabPane = new TabPane();
        tabPane.getStyleClass().add("main-tab-pane");

        // Create tabs
        scheduledTab = new ScheduledReminderTab(
                storageService, soundManager, allReminders,
                scheduler::schedule, this::deleteReminder
        );

        countdownTab = new CountdownTimerTab(
                storageService, soundManager, allReminders,
                scheduler::schedule, this::deleteReminder
        );

        tabPane.getTabs().addAll(scheduledTab, countdownTab);
        root.setCenter(tabPane);

        // Create scene
        Scene scene = new Scene(root, 700, 600);
        loadStylesheet(scene, "/css/styles.css");
        stage.setScene(scene);

        // Handle window close event
        stage.setOnCloseRequest(e -> {
            // Minimize to tray instead of closing (if tray is supported)
            if (systemTrayManager != null && systemTrayManager.isSupported()) {
                e.consume();
                stage.hide();
                systemTrayManager.showNotification("SoundReminder",
                        "App minimized to system tray");
            } else {
                cleanup();
            }
        });
    }

    /**
     * Configures the main stage properties.
     */
    private void configureStage() {
        stage.setTitle("SoundReminder");
        stage.setMinWidth(600);
        stage.setMinHeight(500);
    }

    /**
     * Safely loads a CSS stylesheet and adds it to the scene.
     * If the resource is not found, logs a warning but does not crash.
     *
     * @param scene the scene to add the stylesheet to
     * @param resourcePath the classpath resource path (e.g. "/css/styles.css")
     */
    private static void loadStylesheet(javafx.scene.Scene scene, String resourcePath) {
        java.net.URL url = MainWindow.class.getResource(resourcePath);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        } else {
            LOGGER.warning("CSS resource not found: " + resourcePath +
                    ". Application will run without custom styles.");
        }
    }

    /**
     * Opens the Settings dialog.
     */
    private void openSettings() {
        AppSettings settings = storageService.loadSettings();
        new SettingsDialog(stage, settings, storageService, soundManager);
    }

    /**
     * Loads saved reminders from storage and schedules any that are still active and in the future.
     */
    private void loadSavedReminders() {
        List<Reminder> saved = storageService.loadReminders();
        LocalDateTime now = LocalDateTime.now();

        for (Reminder reminder : saved) {
            if (reminder.isActive() && !reminder.isDone()) {
                // Only schedule if trigger time is in the future
                if (reminder.getTriggerTime().isAfter(now)) {
                    allReminders.add(reminder);
                    scheduler.schedule(reminder);
                } else {
                    // Past-due reminder: fire immediately
                    allReminders.add(reminder);
                    onReminderFired(reminder);
                }
            }
        }

        LOGGER.info("Loaded " + saved.size() + " reminders, " +
                allReminders.size() + " active");

        // Refresh both tabs
        scheduledTab.refreshList();
        countdownTab.refreshList();
    }

    /**
     * Callback invoked when a reminder's trigger time is reached.
     * Starts the alarm sound, shows the notification, and schedules replay if not acknowledged.
     *
     * @param reminder the reminder that fired
     */
    private void onReminderFired(Reminder reminder) {
        LOGGER.info("Reminder fired: " + reminder.getMessage());

        Platform.runLater(() -> {
            // Stop any existing notification
            if (activeNotification != null && activeNotification.isShowing()) {
                activeNotification.hide();
            }

            // Store the firing reminder for replay logic
            this.currentlyFiringReminder = reminder;
            this.replayCount = 0;

            // Start the alarm sound
            soundManager.startAlarm();

            // Show notification
            activeNotification = new NotificationWindow(soundManager, storageService, this::onReminderDone);
            activeNotification.show(reminder);

            // Show system tray notification
            if (systemTrayManager != null) {
                systemTrayManager.showNotification("SoundReminder - " + reminder.getType(),
                        reminder.getMessage());
            }

            // Schedule replay logic:
            // After 60 seconds, stop the sound
            // After 5 minutes (300 seconds), restart sound and notification
            // Repeat until user marks as done
            scheduleReplay(reminder);
        });
    }

    /**
     * Schedules the replay cycle: stop sound after 60s, restart after 300s, repeat.
     *
     * @param reminder the currently firing reminder
     */
    private void scheduleReplay(Reminder reminder) {
        // Stop sound after 60 seconds
        executorService.schedule(() -> {
            if (currentlyFiringReminder != null &&
                    currentlyFiringReminder.getId().equals(reminder.getId()) &&
                    activeNotification != null && !activeNotification.isShowing()) {

                // Sound already stopped by notification hide, but ensure it's stopped
                soundManager.stopAlarm();
                LOGGER.info("Sound stopped after 60 seconds of silence");

                // Restart after 5 minutes (300 seconds) total from original trigger
                // which is 240 seconds after the 60-second stop
                executorService.schedule(() -> {
                    if (currentlyFiringReminder != null &&
                            currentlyFiringReminder.getId().equals(reminder.getId()) &&
                            reminder.isActive() && !reminder.isDone()) {

                        replayCount++;
                        LOGGER.info("Replay #" + replayCount + " for reminder: " + reminder.getMessage());

                        Platform.runLater(() -> {
                            // Restart sound
                            soundManager.startAlarm();

                            // Re-show notification
                            if (activeNotification == null || !activeNotification.isShowing()) {
                                activeNotification = new NotificationWindow(
                                        soundManager, storageService, this::onReminderDone);
                                activeNotification.show(reminder);
                            }

                            // Schedule next replay
                            scheduleReplay(reminder);
                        });
                    }
                }, REPLAY_INTERVAL_SECONDS - REPLAY_DELAY_SECONDS, TimeUnit.SECONDS);
            }
        }, REPLAY_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Callback invoked when the user marks a reminder as done.
     * Stops all replay/sound activity and removes the reminder from active lists.
     *
     * @param reminder the reminder that was marked as done
     */
    private void onReminderDone(Reminder reminder) {
        LOGGER.info("Reminder done: " + reminder.getId());

        // Clear the currently firing reminder reference
        if (currentlyFiringReminder != null &&
                currentlyFiringReminder.getId().equals(reminder.getId())) {
            currentlyFiringReminder = null;
            replayCount = 0;
        }

        // Remove from active list
        allReminders.removeIf(r -> r.getId().equals(reminder.getId()));
        storageService.saveReminders(allReminders);

        // Stop sound (done by NotificationWindow, but ensure)
        soundManager.stopAlarm();

        // Hide notification
        if (activeNotification != null) {
            activeNotification.hide();
            activeNotification = null;
        }

        // Cancel from scheduler
        scheduler.cancel(reminder.getId());

        // Refresh UI
        Platform.runLater(() -> {
            scheduledTab.refreshList();
            countdownTab.refreshList();
        });
    }

    /**
     * Deletes a reminder from the active list and cancels its scheduling.
     *
     * @param reminderId the ID of the reminder to delete
     */
    public void deleteReminder(String reminderId) {
        // Cancel scheduling
        scheduler.cancel(reminderId);

        // Remove from list
        allReminders.removeIf(r -> r.getId().equals(reminderId));

        // Persist
        storageService.saveReminders(allReminders);

        // Refresh UI
        Platform.runLater(() -> {
            scheduledTab.refreshList();
            countdownTab.refreshList();
        });

        LOGGER.info("Reminder deleted: " + reminderId);
    }

    /**
     * Cleans up all resources when the application is closing.
     */
    public void cleanup() {
        LOGGER.info("Cleaning up resources...");

        // Save current state
        storageService.saveReminders(allReminders);

        // Stop sound
        soundManager.stopAlarm();

        // Shutdown scheduler
        executorService.shutdownNow();

        // Shutdown countdown timer executor
        countdownTab.shutdown();

        // Remove system tray icon
        if (systemTrayManager != null) {
            systemTrayManager.removeTrayIcon();
        }

        LOGGER.info("Cleanup complete");
    }
}
