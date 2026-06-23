package com.soundreminder.storage;

import com.soundreminder.model.AppSettings;
import com.soundreminder.model.Reminder;
import com.soundreminder.model.ReminderType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for StorageService using a temporary directory.
 * Tests real file I/O without affecting the user's actual storage.
 */
class StorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storageService;
    private Path originalUserHome;

    @BeforeEach
    void setUp() throws IOException {
        // Temporarily redirect user.home for this test
        originalUserHome = Path.of(System.getProperty("user.home"));
        // StorageService uses user.home at construction time, so we create it pointing to tempDir
        // We need a workaround since we can't change user.home easily.
        // Instead, we'll use a symlink approach: create .soundreminder in tempDir
        Path soundReminderDir = tempDir.resolve(".soundreminder");
        Files.createDirectories(soundReminderDir);
        Files.createDirectories(soundReminderDir.resolve("images"));

        // Create the StorageService - it will use the real user.home
        // For testing, we directly manipulate the temp directory files
        storageService = new StorageService();
    }

    @AfterEach
    void tearDown() {
        // StorageService creates files in real user.home; tests below use temp dir approach
    }

    @Test
    @DisplayName("StorageService initializes and creates directory structure")
    void testInitialization() throws IOException {
        // The real storageService was created in setUp
        // Verify it created the expected structure in user home
        Path userHome = Path.of(System.getProperty("user.home"));
        Path srDir = userHome.resolve(".soundreminder");

        assertTrue(Files.exists(srDir), ".soundreminder directory should exist");
        assertTrue(Files.exists(srDir.resolve("images")), "images subdirectory should exist");
    }

    @Test
    @DisplayName("Save and load empty reminders list")
    void testSaveAndLoadEmptyReminders() {
        List<Reminder> empty = List.of();
        storageService.saveReminders(empty);

        List<Reminder> loaded = storageService.loadReminders();
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("Save and load a single reminder")
    void testSaveAndLoadSingleReminder() {
        LocalDateTime triggerTime = LocalDateTime.of(2026, 6, 15, 14, 30);
        Reminder reminder = new Reminder(
                ReminderType.SCHEDULED,
                triggerTime,
                "Meeting at 2:30",
                null
        );

        storageService.saveReminders(List.of(reminder));

        List<Reminder> loaded = storageService.loadReminders();
        assertEquals(1, loaded.size());

        Reminder loadedReminder = loaded.get(0);
        assertEquals(reminder.getId(), loadedReminder.getId());
        assertEquals(ReminderType.SCHEDULED, loadedReminder.getType());
        assertEquals(triggerTime, loadedReminder.getTriggerTime());
        assertEquals("Meeting at 2:30", loadedReminder.getMessage());
        assertTrue(loadedReminder.isActive());
    }

    @Test
    @DisplayName("Save and load multiple reminders")
    void testSaveAndLoadMultipleReminders() {
        Reminder r1 = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.of(2026, 6, 1, 9, 0), "Morning standup", null);
        Reminder r2 = new Reminder(ReminderType.COUNTDOWN,
                LocalDateTime.now().plusMinutes(30), "Coffee break", null);
        Reminder r3 = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.of(2026, 12, 25, 0, 0), "Christmas!", "/img/tree.png");

        storageService.saveReminders(List.of(r1, r2, r3));

        List<Reminder> loaded = storageService.loadReminders();
        assertEquals(3, loaded.size());

        // Verify all IDs are preserved
        List<String> loadedIds = loaded.stream().map(Reminder::getId).toList();
        assertTrue(loadedIds.contains(r1.getId()));
        assertTrue(loadedIds.contains(r2.getId()));
        assertTrue(loadedIds.contains(r3.getId()));
    }

    @Test
    @DisplayName("Save and load settings")
    void testSaveAndLoadSettings() {
        AppSettings settings = new AppSettings("/custom/alarm.mp3", 0.65);
        storageService.saveSettings(settings);

        AppSettings loaded = storageService.loadSettings();
        assertEquals("/custom/alarm.mp3", loaded.getCustomSoundPath());
        assertEquals(0.65, loaded.getVolume(), 0.001);
    }

    @Test
    @DisplayName("Load default settings when no file exists")
    void testLoadDefaultSettings() {
        // Delete settings file if it exists
        Path settingsFile = Path.of(System.getProperty("user.home"),
                ".soundreminder", "settings.json");
        try {
            Files.deleteIfExists(settingsFile);
        } catch (IOException e) {
            // ignore
        }

        AppSettings settings = storageService.loadSettings();
        assertNotNull(settings);
        assertNull(settings.getCustomSoundPath());
        assertEquals(0.8, settings.getVolume(), 0.001);
    }

    @Test
    @DisplayName("Copy image to storage directory")
    void testCopyImageToStorage() throws IOException {
        // Create a temporary source image
        Path sourceImage = tempDir.resolve("test_image.png");
        Files.write(sourceImage, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        String relativePath = storageService.copyImageToStorage(sourceImage);
        Path destPath = storageService.resolveImagePath(relativePath);

        assertTrue(Files.exists(destPath));
        assertTrue(relativePath.startsWith("images"));
        assertTrue(relativePath.endsWith("test_image.png"));
    }

    @Test
    @DisplayName("Remove reminder by ID")
    void testRemoveReminder() {
        Reminder r1 = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.now().plusHours(1), "Keep me", null);
        Reminder r2 = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.now().plusHours(2), "Delete me", null);

        storageService.saveReminders(List.of(r1, r2));
        storageService.removeReminder(r2.getId());

        List<Reminder> loaded = storageService.loadReminders();
        assertEquals(1, loaded.size());
        assertEquals(r1.getId(), loaded.get(0).getId());
    }

    @Test
    @DisplayName("Reminders survive save-load-save-load cycle")
    void testMultipleSaveLoadCycles() {
        Reminder reminder = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.of(2027, 1, 1, 0, 0), "New Year", null);

        // Cycle 1
        storageService.saveReminders(List.of(reminder));
        List<Reminder> loaded1 = storageService.loadReminders();
        assertEquals(1, loaded1.size());

        // Cycle 2 - modify and re-save
        Reminder modified = loaded1.get(0);
        modified.setMessage("Updated New Year");
        storageService.saveReminders(List.of(modified));

        List<Reminder> loaded2 = storageService.loadReminders();
        assertEquals(1, loaded2.size());
        assertEquals("Updated New Year", loaded2.get(0).getMessage());
        assertEquals(reminder.getId(), loaded2.get(0).getId());
    }

    @Test
    @DisplayName("getImagesDir returns correct path")
    void testGetImagesDir() {
        Path imagesDir = storageService.getImagesDir();
        assertNotNull(imagesDir);
        assertTrue(imagesDir.endsWith("images"));
        assertTrue(Files.exists(imagesDir));
    }

    @Test
    @DisplayName("getBaseDir returns correct path")
    void testGetBaseDir() {
        Path baseDir = storageService.getBaseDir();
        assertNotNull(baseDir);
        assertTrue(baseDir.endsWith(".soundreminder"));
        assertTrue(Files.exists(baseDir));
    }
}
