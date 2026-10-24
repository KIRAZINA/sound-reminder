package com.soundreminder.ui;

import com.soundreminder.model.Reminder;
import com.soundreminder.sound.SoundManager;
import com.soundreminder.storage.StorageService;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Abstraction for displaying reminder notifications.
 * Falls back to the custom JavaFX NotificationWindow.
 */
public class NotificationManager {

    private final SoundManager soundManager;
    private final StorageService storageService;
    private final Consumer<Reminder> onDone;
    private final BiConsumer<Reminder, Long> onSnooze;

    public NotificationManager(SoundManager soundManager, StorageService storageService,
                               Consumer<Reminder> onDone, BiConsumer<Reminder, Long> onSnooze) {
        this.soundManager = soundManager;
        this.storageService = storageService;
        this.onDone = onDone;
        this.onSnooze = onSnooze;
    }

    public NotificationWindow createNotificationWindow() {
        return new NotificationWindow(soundManager, storageService, onDone, onSnooze);
    }
}
