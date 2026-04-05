package com.soundreminder.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Application-wide settings for sound and volume configuration.
 */
public class AppSettings {

    /** Path to the custom sound file, or null to use the default sound. */
    private String customSoundPath;

    /** Volume level from 0.0 (mute) to 1.0 (maximum). Default is 0.8. */
    private double volume;

    /**
     * Creates settings with default values.
     */
    public AppSettings() {
        this.customSoundPath = null;
        this.volume = 0.8;
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

    @Override
    public String toString() {
        return "AppSettings{" +
                "customSoundPath='" + customSoundPath + '\'' +
                ", volume=" + volume +
                '}';
    }
}
