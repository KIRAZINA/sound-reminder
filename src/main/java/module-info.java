/**
 * SoundReminder module declaration.
 * Requires JavaFX modules for UI, media, and Swing interop (for system tray).
 * Opens UI packages to JavaFX for reflection-based access.
 */
module com.soundreminder {
    // JavaFX modules
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.swing;

    // Java Sound API and Desktop
    requires java.desktop;

    // Java Logging API
    requires java.logging;

    // Jackson for JSON
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    // Open packages to JavaFX for CSS and FXML access
    opens com.soundreminder.model to com.fasterxml.jackson.databind;
    opens com.soundreminder to javafx.graphics;
    exports com.soundreminder;
    exports com.soundreminder.model;
    exports com.soundreminder.storage;
    exports com.soundreminder.sound;
    exports com.soundreminder.scheduler;
    exports com.soundreminder.util;
}
