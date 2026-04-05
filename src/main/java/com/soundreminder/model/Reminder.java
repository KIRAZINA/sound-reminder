package com.soundreminder.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single reminder or countdown timer entry.
 * Implements Comparable for priority queue ordering by trigger time.
 */
public class Reminder implements Serializable, Comparable<Reminder> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Unique identifier for this reminder. */
    private String id;

    /** The type of this reminder (scheduled or countdown). */
    private ReminderType type;

    /** The date and time when this reminder should fire. */
    private LocalDateTime triggerTime;

    /** Custom message text to display in the notification. */
    private String message;

    /** File path to an attached image, or null if no image is attached. */
    private String imagePath;

    /** Whether this reminder is currently active. */
    private boolean active;

    /** Whether this reminder has been marked as done. */
    private boolean done;

    /** Timestamp when this reminder was originally created. */
    private LocalDateTime createdAt;

    // No-args constructor for Jackson deserialization
    @SuppressWarnings("unused")
    private Reminder() {
    }

    /**
     * Creates a new reminder with the specified properties.
     *
     * @param type        the reminder type
     * @param triggerTime the time when this reminder should fire
     * @param message     the notification message
     * @param imagePath   path to attached image, or null
     */
    @JsonCreator
    public Reminder(
            @JsonProperty("type") ReminderType type,
            @JsonProperty("triggerTime") LocalDateTime triggerTime,
            @JsonProperty("message") String message,
            @JsonProperty("imagePath") String imagePath) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.triggerTime = triggerTime;
        this.message = message;
        this.imagePath = imagePath;
        this.active = true;
        this.done = false;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Marks this reminder as done and deactivates it.
     */
    public void markAsDone() {
        this.done = true;
        this.active = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ReminderType getType() {
        return type;
    }

    public void setType(ReminderType type) {
        this.type = type;
    }

    public LocalDateTime getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(LocalDateTime triggerTime) {
        this.triggerTime = triggerTime;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public int compareTo(Reminder other) {
        return this.triggerTime.compareTo(other.triggerTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reminder reminder = (Reminder) o;
        return Objects.equals(id, reminder.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Reminder{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", triggerTime=" + triggerTime +
                ", message='" + message + '\'' +
                ", active=" + active +
                '}';
    }
}
