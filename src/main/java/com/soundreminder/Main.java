package com.soundreminder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.soundreminder.model.AppSettings;
import com.soundreminder.storage.StorageService;
import com.soundreminder.ui.MainWindow;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * SoundReminder - A cross-platform alarm/reminder application.
 * Main entry point. Initializes logging, storage, and launches the main window.
 *
 * @version 1.0.0
 * @since 2026
 */
public class Main extends Application {

    /** Application-wide logger. */
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    /** Formatter for log file timestamps. */
    private static final DateTimeFormatter LOG_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * JavaFX application entry point.
     *
     * @param primaryStage the primary stage provided by JavaFX
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            LOGGER.info("=== SoundReminder Starting ===");

            // Initialize storage service
            StorageService storageService = new StorageService();

            // Load application settings
            AppSettings settings = storageService.loadSettings();
            LOGGER.info("Settings loaded: " + settings);

            // Create and show the main window
            MainWindow mainWindow = new MainWindow(primaryStage, storageService, settings);

            // Log startup completion
            LOGGER.info("SoundReminder started successfully at " +
                    LocalDateTime.now().format(LOG_TIMESTAMP));

        } catch (IOException e) {
            LOGGER.severe("FATAL: Failed to initialize storage: " + e.getMessage());
            showErrorAndExit("Failed to initialize application storage.\n\n" +
                    "Error: " + e.getMessage() + "\n\n" +
                    "Please ensure you have write permissions in your home directory.");
        } catch (Exception e) {
            LOGGER.severe("FATAL: Unexpected error during startup: " + e.getMessage());
            showErrorAndExit("An unexpected error occurred during startup:\n\n" +
                    e.getMessage());
        }
    }

    /**
     * Called when the application is shutting down.
     * Performs cleanup of all resources.
     */
    @Override
    public void stop() {
        LOGGER.info("SoundReminder shutting down...");
        // MainWindow cleanup will be called from the stage close handler
        LOGGER.info("SoundReminder stopped");
    }

    /**
     * Initializes the application's logging system.
     * Configures both console and file logging.
     */
    private static void initLogging() {
        try {
            // Get or create the root logger
            Logger rootLogger = Logger.getLogger("");

            // Remove default handlers
            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            // Determine log file path
            String userHome = System.getProperty("user.home");
            Path logDir = Path.of(userHome, ".soundreminder", "logs");
            Files.createDirectories(logDir);

            String logFileName = "soundreminder_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".log";
            Path logFile = logDir.resolve(logFileName);

            // Console handler - WARNING level and above
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.WARNING);
            consoleHandler.setFormatter(new SimpleFormatter());
            consoleHandler.setEncoding("UTF-8");

            // File handler - ALL levels
            FileHandler fileHandler = new FileHandler(logFile.toString(), true);
            fileHandler.setLevel(Level.ALL);
            fileHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%1$tF %1$tT] [%2$-7s] %3$s - %4$s%n",
                            LocalDateTime.now(),
                            record.getLevel().getLocalizedName(),
                            record.getLoggerName(),
                            record.getMessage());
                }
            });
            fileHandler.setEncoding("UTF-8");

            // Add handlers to root logger
            rootLogger.addHandler(consoleHandler);
            rootLogger.addHandler(fileHandler);
            rootLogger.setLevel(Level.ALL);

            LOGGER.info("Logging initialized. Log file: " + logFile);

        } catch (IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }

    /**
     * Shows a fatal error dialog and exits the application.
     *
     * @param message the error message to display
     */
    private void showErrorAndExit(String message) {
        // Show error in a simple dialog (JavaFX might not be fully initialized)
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("SoundReminder - Fatal Error");
        alert.setHeaderText("Application cannot start");
        alert.setContentText(message);
        alert.showAndWait();

        Platform.exit();
        System.exit(1);
    }

    /**
     * Main method entry point. Initializes logging and launches the JavaFX application.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        // Initialize logging before JavaFX starts
        initLogging();

        // Launch JavaFX application
        launch(args);
    }
}
