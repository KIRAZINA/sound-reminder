package com.soundreminder.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.soundreminder.model.AppSettings;
import com.soundreminder.model.Reminder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles all file-based JSON persistence for reminders and settings.
 * Stores data in the user's home directory under ~/.soundreminder.
 * Thread-safe with read-write locks for concurrent access.
 */
public class StorageService {

    private static final Logger LOGGER = Logger.getLogger(StorageService.class.getName());

    /** Base directory for all app data. */
    private final Path baseDir;

    /** Path to the reminders JSON file. */
    private final Path remindersFile;

    /** Path to the settings JSON file. */
    private final Path settingsFile;

    /** Path to the directory where attached images are stored. */
    private final Path imagesDir;

    /** Jackson ObjectMapper for JSON serialization. */
    private final ObjectMapper objectMapper;

    /** Read-write lock for thread-safe file access. */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a new StorageService and initializes the storage directory structure.
     *
     * @throws IOException if the directory structure cannot be created
     */
    public StorageService() throws IOException {
        // Determine base storage directory based on platform
        String userHome = System.getProperty("user.home");
        if (System.getProperty("os.name").toLowerCase().contains("android")) {
            // Android-specific path
            baseDir = Paths.get(System.getProperty("app.data.dir", userHome), ".soundreminder");
        } else {
            // Desktop path (Windows, Linux, macOS)
            baseDir = Paths.get(userHome, ".soundreminder");
        }

        remindersFile = baseDir.resolve("reminders.json");
        settingsFile = baseDir.resolve("settings.json");
        imagesDir = baseDir.resolve("images");

        // Create directory structure
        Files.createDirectories(baseDir);
        Files.createDirectories(imagesDir);

        // Configure Jackson ObjectMapper with Java 8 time support
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        LOGGER.info("Storage initialized at: " + baseDir);
    }

    /**
     * Loads all reminders from disk.
     *
     * @return list of all reminders (never null, empty list if file doesn't exist)
     */
    public List<Reminder> loadReminders() {
        lock.readLock().lock();
        try {
            if (!Files.exists(remindersFile)) {
                LOGGER.info("No reminders file found, returning empty list");
                return new ArrayList<>();
            }

            String json = Files.readString(remindersFile);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }

            List<Reminder> reminders = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Reminder.class));
            LOGGER.info("Loaded " + reminders.size() + " reminders from disk");
            return reminders;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load reminders", e);
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Saves all reminders to disk atomically.
     *
     * @param reminders the complete list of reminders to save
     */
    public void saveReminders(List<Reminder> reminders) {
        lock.writeLock().lock();
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(reminders);
            // Write to temp file first, then rename for atomicity
            Path tempFile = remindersFile.resolveSibling("reminders.json.tmp");
            Files.writeString(tempFile, json);
            Files.move(tempFile, remindersFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Saved " + reminders.size() + " reminders to disk");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save reminders", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Loads application settings from disk.
     *
     * @return AppSettings object (returns defaults if file doesn't exist)
     */
    public AppSettings loadSettings() {
        lock.readLock().lock();
        try {
            if (!Files.exists(settingsFile)) {
                LOGGER.info("No settings file found, returning defaults");
                return new AppSettings();
            }

            String json = Files.readString(settingsFile);
            if (json == null || json.isBlank()) {
                return new AppSettings();
            }

            AppSettings settings = objectMapper.readValue(json, AppSettings.class);
            LOGGER.info("Loaded settings from disk");
            return settings;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load settings", e);
            return new AppSettings();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Saves application settings to disk.
     *
     * @param settings the settings to save
     */
    public void saveSettings(AppSettings settings) {
        lock.writeLock().lock();
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings);
            Path tempFile = settingsFile.resolveSibling("settings.json.tmp");
            Files.writeString(tempFile, json);
            Files.move(tempFile, settingsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Saved settings to disk");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save settings", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Copies an image file to the app's images directory and returns the new path.
     *
     * @param sourcePath the path to the source image file
     * @return the path of the copied image within the app's storage
     * @throws IOException if the copy operation fails
     */
    public Path copyImageToStorage(Path sourcePath) throws IOException {
        String fileName = sourcePath.getFileName().toString();
        // Prepend timestamp to avoid name collisions
        String timestamp = String.valueOf(System.currentTimeMillis());
        String newName = timestamp + "_" + fileName;
        Path destPath = imagesDir.resolve(newName);
        Files.copy(sourcePath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        LOGGER.info("Copied image to storage: " + destPath);
        return destPath;
    }

    /**
     * Gets the path to the images directory.
     *
     * @return the images directory path
     */
    public Path getImagesDir() {
        return imagesDir;
    }

    /**
     * Gets the base storage directory path.
     *
     * @return the base directory path
     */
    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * Deletes an inactive or done reminder from storage.
     *
     * @param reminderId the ID of the reminder to remove
     */
    public void removeReminder(String reminderId) {
        List<Reminder> reminders = loadReminders();
        reminders.removeIf(r -> r.getId().equals(reminderId));
        saveReminders(reminders);
        LOGGER.info("Removed reminder: " + reminderId);
    }
}
