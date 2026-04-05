package com.soundreminder.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the AppSettings model class.
 */
class AppSettingsTest {

    @Test
    @DisplayName("Default constructor sets defaults")
    void testDefaultConstructor() {
        AppSettings settings = new AppSettings();

        assertNull(settings.getCustomSoundPath());
        assertEquals(0.8, settings.getVolume(), 0.001);
    }

    @Test
    @DisplayName("Parameterized constructor sets values")
    void testParameterizedConstructor() {
        AppSettings settings = new AppSettings("/custom/sound.mp3", 0.5);

        assertEquals("/custom/sound.mp3", settings.getCustomSoundPath());
        assertEquals(0.5, settings.getVolume(), 0.001);
    }

    @Test
    @DisplayName("setVolume clamps to 0.0 minimum")
    void testVolumeClampMin() {
        AppSettings settings = new AppSettings();
        settings.setVolume(-0.5);

        assertEquals(0.0, settings.getVolume(), 0.001);
    }

    @Test
    @DisplayName("setVolume clamps to 1.0 maximum")
    void testVolumeClampMax() {
        AppSettings settings = new AppSettings();
        settings.setVolume(1.5);

        assertEquals(1.0, settings.getVolume(), 0.001);
    }

    @Test
    @DisplayName("setVolume accepts valid range")
    void testVolumeValidRange() {
        AppSettings settings = new AppSettings();

        settings.setVolume(0.0);
        assertEquals(0.0, settings.getVolume(), 0.001);

        settings.setVolume(0.5);
        assertEquals(0.5, settings.getVolume(), 0.001);

        settings.setVolume(1.0);
        assertEquals(1.0, settings.getVolume(), 0.001);
    }

    @Test
    @DisplayName("setCustomSoundPath updates correctly")
    void testSetCustomSoundPath() {
        AppSettings settings = new AppSettings();

        settings.setCustomSoundPath("/path/to/sound.wav");
        assertEquals("/path/to/sound.wav", settings.getCustomSoundPath());

        settings.setCustomSoundPath(null);
        assertNull(settings.getCustomSoundPath());
    }

    @Test
    @DisplayName("toString contains volume and sound path")
    void testToString() {
        AppSettings settings = new AppSettings("/sound.wav", 0.75);

        String str = settings.toString();
        assertTrue(str.contains("/sound.wav"));
        assertTrue(str.contains("0.75"));
    }
}
