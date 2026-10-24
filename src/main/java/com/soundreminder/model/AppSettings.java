package com.soundreminder.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Application-wide settings for sound and volume configuration.
 */
public class AppSettings {

    public enum Theme { DARK, LIGHT }

    /** Path to the custom sound file, or null to use the default sound. */
    private String customSoundPath;

    /** Volume level from 0.0 (mute) to 1.0 (maximum). Default is 0.8. */
    private double volume;

    /** Whether local reminder storage is encrypted. */
    private boolean encryptionEnabled;

    /** Optional master password used to derive the storage encryption key. */
    private String encryptionPassword;

    /** UI theme preference. */
    private Theme theme;

    /**
     * Creates settings with default values.
     */
    public AppSettings() {
        this.customSoundPath = null;
        this.volume = 0.8;
        this.encryptionEnabled = false;
        this.encryptionPassword = null;
        this.theme = Theme.DARK;
    }

    @JsonCreator
    public AppSettings(
            @JsonProperty("customSoundPath") String customSoundPath,
            @JsonProperty("volume") double volume) {
        this.customSoundPath = customSoundPath;
        this.volume = volume;
    }

    public String getCustomSoundPath() {
        return customSoundPath;
    }

    public void setCustomSoundPath(String customSoundPath) {
        this.customSoundPath = customSoundPath;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = Math.max(0.0, Math.min(1.0, volume));
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public void setEncryptionEnabled(boolean encryptionEnabled) {
        this.encryptionEnabled = encryptionEnabled;
    }

    public String getEncryptionPassword() {
        return encryptionPassword;
    }

    public void setEncryptionPassword(String encryptionPassword) {
        this.encryptionPassword = encryptionPassword;
    }

    public Theme getTheme() {
        return theme != null ? theme : Theme.DARK;
    }

    public void setTheme(Theme theme) {
        this.theme = theme != null ? theme : Theme.DARK;
    }

    @Override
    public String toString() {
        return "AppSettings{" +
                "customSoundPath='" + customSoundPath + '\'' +
                ", volume=" + volume +
                '}';
    }
}
