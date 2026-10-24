package com.soundreminder.ui;

import com.soundreminder.model.Reminder;
import com.soundreminder.sound.SoundManager;
import com.soundreminder.storage.StorageService;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Abstraction for displaying reminder notifications.
 * Attempts native OS notification via SystemTray when available, then creates
 * the custom JavaFX NotificationWindow.
 */
public class NotificationManager {

    private static final Logger LOGGER = Logger.getLogger(NotificationManager.class.getName());

    private final SoundManager soundManager;
    private final StorageService storageService;
    private final Consumer<Reminder> onDone;
    private final BiConsumer<Reminder, Long> onSnooze;
    private SystemTrayManager systemTrayManager;

    public NotificationManager(SoundManager soundManager, StorageService storageService,
                               Consumer<Reminder> onDone, BiConsumer<Reminder, Long> onSnooze) {
        this.soundManager = soundManager;
        this.storageService = storageService;
        this.onDone = onDone;
        this.onSnooze = onSnooze;
    }

    public void setSystemTrayManager(SystemTrayManager systemTrayManager) {
        this.systemTrayManager = systemTrayManager;
    }

    public void tryNativeNotification(Reminder reminder) {
        if (systemTrayManager == null || !systemTrayManager.isSupported()) {
            return;
        }
        try {
            systemTrayManager.showNotification("SoundReminder", reminder.getMessage());
        } catch (Exception e) {
            LOGGER.warning("Native notification failed, falling back to JavaFX: " + e.getMessage());
        }
    }

    public NotificationWindow createNotificationWindow() {
        return new NotificationWindow(soundManager, storageService, onDone, onSnooze);
    }
}

