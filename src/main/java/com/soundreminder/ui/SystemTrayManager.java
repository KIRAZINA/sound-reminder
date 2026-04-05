package com.soundreminder.ui;

import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.soundreminder.model.Reminder;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/**
 * Manages the system tray icon and its context menu.
 * Provides quick access: Open app, Exit, Show pending reminders.
 * Only available on desktop platforms that support SystemTray.
 */
public class SystemTrayManager {

    private static final Logger LOGGER = Logger.getLogger(SystemTrayManager.class.getName());

    /** The AWT SystemTray instance. Null if not supported. */
    private final SystemTray systemTray;

    /** The tray icon displayed in the system tray. */
    private TrayIcon trayIcon;

    /** The main application stage. */
    private final Stage mainStage;

    /** Provider for the list of active reminders. */
    private final ReminderProvider reminderProvider;

    /**
     * Functional interface for providing the current list of active reminders.
     */
    @FunctionalInterface
    public interface ReminderProvider {
        List<Reminder> getActiveReminders();
    }

    /**
     * Creates and initializes the system tray icon if the platform supports it.
     *
     * @param mainStage         the main application stage
     * @param reminderProvider  provider for active reminders
     */
    public SystemTrayManager(Stage mainStage, ReminderProvider reminderProvider) {
        this.mainStage = mainStage;
        this.reminderProvider = reminderProvider;
        this.systemTray = SystemTray.isSupported() ? SystemTray.getSystemTray() : null;

        if (systemTray != null) {
            initializeTray();
        } else {
            LOGGER.info("SystemTray is not supported on this platform");
        }
    }

    /**
     * Initializes the tray icon with a popup menu.
     */
    private void initializeTray() {
        try {
            // Create popup menu
            PopupMenu popup = new PopupMenu();

            // "Open App" menu item
            MenuItem openItem = new MenuItem("Open SoundReminder");
            openItem.addActionListener(e -> Platform.runLater(() -> {
                mainStage.setIconified(false);
                mainStage.show();
                mainStage.toFront();
            }));
            popup.add(openItem);

            // "Show Pending Reminders" menu item
            MenuItem pendingItem = new MenuItem("Show Pending Reminders");
            pendingItem.addActionListener(e -> showPendingReminders());
            popup.add(pendingItem);

            // Separator
            popup.addSeparator();

            // "Exit" menu item
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> Platform.exit());
            popup.add(exitItem);

            // Create tray icon
            java.net.URL iconUrl = getClass().getResource("/icons/tray-icon.png");
            if (iconUrl == null) {
                LOGGER.warning("Tray icon resource not found: /icons/tray-icon.png. Using system tray without icon.");
                // Create a minimal 1x1 transparent pixel as fallback
                trayIcon = new TrayIcon(createFallbackImage(), "SoundReminder", popup);
                trayIcon.setImageAutoSize(true);
            } else {
                Image iconImage = Toolkit.getDefaultToolkit().getImage(iconUrl);
                trayIcon = new TrayIcon(iconImage, "SoundReminder", popup);
                trayIcon.setImageAutoSize(true);
            }

            // Double-click to open app
            trayIcon.addActionListener(e -> Platform.runLater(() -> {
                mainStage.setIconified(false);
                mainStage.show();
                mainStage.toFront();
            }));

            systemTray.add(trayIcon);
            LOGGER.info("System tray icon added successfully");

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to add system tray icon", e);
        }
    }

    /**
     * Shows an alert dialog with the list of pending active reminders.
     */
    private void showPendingReminders() {
        List<Reminder> activeReminders = reminderProvider.getActiveReminders();

        if (activeReminders.isEmpty()) {
            Platform.runLater(() -> {
                Alert alert = new Alert(AlertType.INFORMATION,
                        "No active reminders.", ButtonType.OK);
                alert.setTitle("Pending Reminders");
                alert.setHeaderText(null);
                alert.showAndWait();
            });
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Active Reminders (").append(activeReminders.size()).append("):\n\n");
            for (int i = 0; i < activeReminders.size(); i++) {
                Reminder r = activeReminders.get(i);
                String type = r.getType() == com.soundreminder.model.ReminderType.COUNTDOWN
                        ? "[Timer]" : "[Scheduled]";
                sb.append(i + 1).append(". ").append(type).append(" ")
                        .append(r.getTriggerTime().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                        .append(" - ").append(r.getMessage()).append("\n");
            }

            final String message = sb.toString();
            Platform.runLater(() -> {
                Alert alert = new Alert(AlertType.INFORMATION, message, ButtonType.OK);
                alert.setTitle("Pending Reminders");
                alert.setHeaderText("Active Reminders:");
                alert.getDialogPane().setPrefSize(450, 300);
                alert.showAndWait();
            });
        }
    }

    /**
     * Creates a simple 16x16 blue circle image as a fallback tray icon.
     *
     * @return an AWT Image of a blue circle
     */
    private Image createFallbackImage() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(0, 120, 212)); // Windows blue
        g.fillOval(1, 1, 14, 14);
        g.dispose();
        return image;
    }

    /**
     * Displays a notification balloon in the system tray.
     *
     * @param title   the balloon title
     * @param message the balloon message
     */
    public void showNotification(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, MessageType.INFO);
        }
    }

    /**
     * Removes the tray icon from the system tray.
     */
    public void removeTrayIcon() {
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
            LOGGER.info("System tray icon removed");
        }
    }

    /**
     * Checks whether the system tray is supported on this platform.
     *
     * @return true if SystemTray is supported
     */
    public boolean isSupported() {
        return systemTray != null;
    }
}
