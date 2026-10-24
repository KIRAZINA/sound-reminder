package com.soundreminder.ui;

import java.util.logging.Logger;

/**
 * Best-effort global hotkey registration. Falls back gracefully if unsupported.
 */
public class GlobalHotkeyManager {

    private static final Logger LOGGER = Logger.getLogger(GlobalHotkeyManager.class.getName());

    public GlobalHotkeyManager(Runnable onTrigger) {
        try {
            // Native global hotkey hooks are platform-specific and not available
            // through standard JDK APIs. Log and continue without crashing.
            LOGGER.warning("Global hotkey registration is not supported in this environment; " +
                    "Quick-Add hotkey is disabled.");
        } catch (Exception e) {
            LOGGER.warning("Failed to register global hotkey: " + e.getMessage());
        }
    }

    public void shutdown() {
    }
}
