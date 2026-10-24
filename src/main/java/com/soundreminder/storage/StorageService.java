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

            String json;
            AppSettings settings = loadSettings();
            if (settings.isEncryptionEnabled() && settings.getEncryptionPassword() != null
                    && !settings.getEncryptionPassword().isBlank()) {
                String encrypted = Files.readString(remindersFile).trim();
                if (encrypted.isBlank()) {
                    return new ArrayList<>();
                }
                json = com.soundreminder.util.CryptoUtils.decryptFromBase64(
                        encrypted, settings.getEncryptionPassword().toCharArray());
            } else {
                json = Files.readString(remindersFile);
                if (json == null || json.isBlank()) {
                    return new ArrayList<>();
                }
            }

            List<Reminder> reminders = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Reminder.class));
            LOGGER.info("Loaded " + reminders.size() + " reminders from disk");
            return reminders;
        } catch (Exception e) {
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
        Path tempFile = null;
        try {
            AppSettings settings = loadSettings();
            String content;
            if (settings.isEncryptionEnabled() && settings.getEncryptionPassword() != null
                    && !settings.getEncryptionPassword().isBlank()) {
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(reminders);
                content = com.soundreminder.util.CryptoUtils.encryptToBase64(
                        json, settings.getEncryptionPassword().toCharArray());
            } else {
                content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(reminders);
            }

            // Write to temp file first, then rename for atomicity
            tempFile = remindersFile.resolveSibling("reminders.json.tmp");
            Files.writeString(tempFile, content);
            Files.move(tempFile, remindersFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tempFile = null;
            LOGGER.info("Saved " + reminders.size() + " reminders to disk");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save reminders", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
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
        Path tempFile = null;
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings);
            tempFile = settingsFile.resolveSibling("settings.json.tmp");
            Files.writeString(tempFile, json);
            Files.move(tempFile, settingsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tempFile = null;
            LOGGER.info("Saved settings to disk");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save settings", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
            lock.writeLock().unlock();
        }
    }

    /**
     * Copies an image file to the app's images directory and returns the
     * relative path (relative to the storage root) for portable storage.
     *
     * @param sourcePath the path to the source image file
     * @return the relative path from baseDir to the copied image (e.g. "images/1687654321_photo.png")
     * @throws IOException if the copy operation fails
     */
    public String copyImageToStorage(Path sourcePath) throws IOException {
        Path sourceFileName = sourcePath.getFileName();
        if (sourceFileName == null) {
            throw new IllegalArgumentException("Source path must include a file name");
        }
        String fileName = sourceFileName.toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String newName = timestamp + "_" + fileName;
        Path destPath = imagesDir.resolve(newName);
        Files.copy(sourcePath, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String relativePath = baseDir.relativize(destPath).toString().replace("\\", "/");
        LOGGER.info("Copied image to storage: " + destPath + " (relative: " + relativePath + ")");
        return relativePath;
    }

    /**
     * Resolves a potentially relative image path to an absolute path.
     * If the path is already absolute, returns it unchanged.
     * If the path is relative, resolves it against the storage base directory.
     *
     * @param imagePath the stored image path (relative or absolute)
     * @return the absolute path to the image file
     */
    public Path resolveImagePath(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        Path path = Path.of(imagePath).normalize();
        if (path.isAbsolute()) {
            return null;
        }
        Path resolved = baseDir.resolve(path).normalize();
        if (!resolved.startsWith(baseDir)) {
            return null;
        }
        return resolved;
    }

    /**
     * Exports reminders, settings, and images into a zip archive.
     */
    public void exportBackup(Path destinationZip) throws IOException {
        lock.writeLock().lock();
        try {
            if (destinationZip.getParent() != null) {
                Files.createDirectories(destinationZip.getParent());
            }
            try (java.util.zip.ZipOutputStream zos =
                         new java.util.zip.ZipOutputStream(Files.newOutputStream(destinationZip))) {
                addFileToZip(zos, remindersFile, "reminders.json");
                addFileToZip(zos, settingsFile, "settings.json");
                if (Files.exists(imagesDir)) {
                    Files.walk(imagesDir).filter(Files::isRegularFile).forEach(path -> {
                        String entryName = "images/" + imagesDir.relativize(path).toString().replace("\\", "/");
                        try {
                            zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
            LOGGER.info("Backup exported to: " + destinationZip);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void addFileToZip(java.util.zip.ZipOutputStream zos, Path file, String entryName) throws IOException {
        if (!Files.exists(file)) return;
        zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
    }

    /**
     * Imports a backup zip, replacing current files and images.
     */
    public void importBackup(Path sourceZip) throws IOException {
        lock.writeLock().lock();
        Path rollbackDir = null;
        try {
            rollbackDir = Files.createTempDirectory("soundreminder-rollback-");
            // rollback current state
            if (Files.exists(remindersFile)) {
                Files.copy(remindersFile, rollbackDir.resolve("reminders.json"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(settingsFile)) {
                Files.copy(settingsFile, rollbackDir.resolve("settings.json"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(imagesDir)) {
                Path rollbackImages = rollbackDir.resolve("images");
                Files.createDirectories(rollbackImages);
                Files.walk(imagesDir).filter(Files::isRegularFile).forEach(p -> {
                    try {
                        Path target = rollbackImages.resolve(imagesDir.relativize(p).toString());
                        Files.createDirectories(target.getParent());
                        Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            // extract
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(sourceZip))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }
                    String name = entry.getName().replace("\\", "/");
                    if (name.contains("..")) {
                        throw new IOException("Unsafe zip entry: " + name);
                    }
                    Path target;
                    if (name.equals("reminders.json")) {
                        target = remindersFile;
                    } else if (name.equals("settings.json")) {
                        target = settingsFile;
                    } else if (name.startsWith("images/")) {
                        target = imagesDir.resolve(name.substring("images/".length()));
                    } else {
                        zis.closeEntry();
                        continue;
                    }
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    Files.copy(zis, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    zis.closeEntry();
                }
            }
            LOGGER.info("Backup imported from: " + sourceZip);
        } catch (Exception e) {
            // rollback
            if (rollbackDir != null) {
                try {
                    if (Files.exists(rollbackDir.resolve("reminders.json"))) {
                        Files.copy(rollbackDir.resolve("reminders.json"), remindersFile,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (Files.exists(rollbackDir.resolve("settings.json"))) {
                        Files.copy(rollbackDir.resolve("settings.json"), settingsFile,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ignored) {
                }
            }
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        } finally {
            if (rollbackDir != null) {
                try {
                    Files.walk(rollbackDir)
                            .sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                            });
                } catch (IOException ignored) {
                }
            }
            lock.writeLock().unlock();
        }
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
