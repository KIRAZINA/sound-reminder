package com.soundreminder.sound;

import com.soundreminder.model.AppSettings;
import com.soundreminder.util.AlarmSoundGenerator;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages alarm sound playback using Java Sound API.
 * Supports both bundled default sound and custom user-selected sound files.
 * Handles volume control and looping playback.
 */
public class SoundManager {

    private static final Logger LOGGER = Logger.getLogger(SoundManager.class.getName());

    /** Resource path for the bundled default alarm sound (WAV format). */
    private static final String DEFAULT_SOUND_RESOURCE = "/sounds/alarm.wav";

    /** Current application settings. */
    private AppSettings settings;

    /** The currently active Clip for sound playback. Null if no sound is playing. */
    private Clip activeClip;

    /** Logical alarm state for headless environments where audio lines are unavailable. */
    private volatile boolean alarmActive = false;

    /** The loaded audio data for the current sound. Cached to avoid repeated file reads. */
    private byte[] soundData;

    /**
     * Creates a new SoundManager with the given settings.
     *
     * @param settings the current application settings
     */
    public SoundManager(AppSettings settings) {
        this.settings = settings;
    }

    /**
     * Updates the settings reference. Call this when settings change.
     *
     * @param settings the updated settings
     */
    public void updateSettings(AppSettings settings) {
        this.settings = settings;
        // Clear cached sound data so the next playback uses the new sound
        this.soundData = null;
    }

    /**
     * Starts playing the alarm sound in a loop. The volume is set according to current settings.
     * If a sound is already playing, it is stopped first.
     */
    public void startAlarm() {
        stopAlarm();
        alarmActive = true;

        try {
            // Load sound data if not already cached
            if (soundData == null) {
                soundData = loadSoundData();
            }

            if (soundData == null) {
                LOGGER.severe("No sound data available");
                alarmActive = false;
                return;
            }

            // Create a new Clip from the cached audio data
            try (java.io.InputStream bais = new java.io.ByteArrayInputStream(soundData);
                 AudioInputStream ais = AudioSystem.getAudioInputStream(bais)) {
                Clip clip = AudioSystem.getClip();
                clip.open(ais);

                // Set volume according to settings
                setClipVolume(clip, settings.getVolume());

                // Start looping indefinitely
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            activeClip = clip;
            alarmActive = true;
            }

            LOGGER.info("Alarm started, volume: " + settings.getVolume());
        } catch (LineUnavailableException | IOException | UnsupportedAudioFileException | IllegalArgumentException e) {
            if (e instanceof IllegalArgumentException) {
                LOGGER.log(Level.WARNING, "Audio line unsupported or headless environment detected, suppressing audio playback.");
            } else {
                alarmActive = false;
                LOGGER.log(Level.SEVERE, "Failed to start alarm", e);
            }
        }
    }

    /**
     * Stops the currently playing alarm sound and releases the Clip resource.
     * Safe to call even if no alarm is playing.
     */
    public void stopAlarm() {
        if (activeClip != null) {
            try {
                activeClip.stop();
                activeClip.close();
                LOGGER.info("Alarm stopped");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error stopping alarm", e);
            } finally {
                activeClip = null;
            }
        }
        alarmActive = false;
    }

    public boolean isAudioSupported() {
        try {
            return javax.sound.sampled.AudioSystem.getMixerInfo().length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether a sound is currently playing.
     *
     * @return true if the alarm is active, false otherwise
     */
    public boolean isPlaying() {
        try {
            if (!alarmActive) return false;
            if (activeClip != null) {
                return activeClip.isRunning();
            }
            return !isAudioSupported();
        } catch (Exception e) {
            return alarmActive;
        }
    }

    /**
     * Loads sound data from either the custom sound path (if set) or the bundled default.
     * Falls back to programmatically generated sound if no resource file is found.
     *
     * @return the raw audio data as bytes, or null if all loading methods fail
     */
    private byte[] loadSoundData() {
        // Try custom sound first
        if (settings.getCustomSoundPath() != null && !settings.getCustomSoundPath().isBlank()) {
            Path customPath = Path.of(settings.getCustomSoundPath());
            if (Files.exists(customPath)) {
                try {
                    return Files.readAllBytes(customPath);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load custom sound, falling back to default", e);
                }
            }
        }

        // Try bundled default sound resource
        try (InputStream is = SoundManager.class.getResourceAsStream(DEFAULT_SOUND_RESOURCE)) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load default sound resource", e);
        }

        // Last resort: generate the alarm sound programmatically
        LOGGER.info("No bundled sound found, generating alarm sound programmatically");
        try {
            return AlarmSoundGenerator.generateAlarmWav();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate alarm sound programmatically", e);
        }

        LOGGER.severe("No sound data available from any source");
        return null;
    }

    /**
     * Sets the volume on a Clip using the MASTER_GAIN control.
     *
     * @param clip   the Clip to adjust
     * @param volume volume level from 0.0 (mute) to 1.0 (maximum)
     */
    private void setClipVolume(Clip clip, double volume) {
        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        if (gainControl != null) {
            // Convert linear volume (0-1) to decibel gain
            // Minimum gain (most negative) to maximum gain (0 dB)
            float minDb = gainControl.getMinimum();
            float maxDb = gainControl.getMaximum();
            float range = maxDb - minDb;
            float gain = minDb + (float) volume * range;
            gainControl.setValue(gain);
            LOGGER.fine("Volume set to " + volume + " (gain: " + gain + " dB)");
        }
    }

    /**
     * Tests the current sound at the configured volume. Used for settings preview.
     */
    public void testSound() {
        new Thread(() -> {
            startAlarm();
            try {
                Thread.sleep(2000); // Play for 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stopAlarm();
        }, "SoundTestThread").start();
    }
}
