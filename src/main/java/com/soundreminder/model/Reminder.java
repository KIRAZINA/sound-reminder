package com.soundreminder.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Reminder implements Serializable, Comparable<Reminder> {

    @Serial
    private static final long serialVersionUID = 2L;

    private String id;
    private ReminderType type;
    private LocalDateTime triggerTime;
    private String message;
    private String imagePath;
    private boolean active;
    private boolean done;
    private LocalDateTime createdAt;
    private RecurrenceRule recurrenceRule;
    private LocalDateTime lastFiredAt;
    private int snoozeCount;
    private LocalDateTime lastSnoozedAt;
    private Set<String> tags;
    private Priority priority;

    @SuppressWarnings("unused")
    private Reminder() {
    }

    @JsonCreator
    public Reminder(
            @JsonProperty("type") ReminderType type,
            @JsonProperty("triggerTime") LocalDateTime triggerTime,
            @JsonProperty("message") String message,
            @JsonProperty("imagePath") String imagePath,
            @JsonProperty("recurrenceRule") RecurrenceRule recurrenceRule,
            @JsonProperty("priority") Priority priority,
            @JsonProperty("tags") Set<String> tags) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.triggerTime = triggerTime;
        this.message = message;
        this.imagePath = imagePath;
        this.recurrenceRule = recurrenceRule;
        this.priority = priority != null ? priority : Priority.NORMAL;
        this.tags = tags != null ? tags : new HashSet<>();
        this.active = true;
        this.done = false;
        this.createdAt = LocalDateTime.now();
        this.snoozeCount = 0;
    }

    public Reminder(ReminderType type, LocalDateTime triggerTime, String message, String imagePath) {
        this(type, triggerTime, message, imagePath, null, Priority.NORMAL, new HashSet<>());
    }

    public void markAsDone() {
        this.done = true;
        this.active = false;
    }

    public void snoozeUntil(LocalDateTime newTime) {
        this.triggerTime = newTime;
        this.snoozeCount++;
        this.lastSnoozedAt = LocalDateTime.now();
        this.active = true;
        this.done = false;
    }

    public boolean isRecurring() {
        return recurrenceRule != null;
    }

    public LocalDateTime computeNextOccurrence() {
        if (recurrenceRule == null) return null;
        LocalDateTime from = lastFiredAt != null ? lastFiredAt : triggerTime;
        return recurrenceRule.computeNextOccurrence(from);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ReminderType getType() { return type; }
    public void setType(ReminderType type) { this.type = type; }

    public LocalDateTime getTriggerTime() { return triggerTime; }
    public void setTriggerTime(LocalDateTime triggerTime) { this.triggerTime = triggerTime; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public RecurrenceRule getRecurrenceRule() { return recurrenceRule; }
    public void setRecurrenceRule(RecurrenceRule recurrenceRule) { this.recurrenceRule = recurrenceRule; }

    public LocalDateTime getLastFiredAt() { return lastFiredAt; }
    public void setLastFiredAt(LocalDateTime lastFiredAt) { this.lastFiredAt = lastFiredAt; }

    public int getSnoozeCount() { return snoozeCount; }
    public void setSnoozeCount(int snoozeCount) { this.snoozeCount = snoozeCount; }

    public LocalDateTime getLastSnoozedAt() { return lastSnoozedAt; }
    public void setLastSnoozedAt(LocalDateTime lastSnoozedAt) { this.lastSnoozedAt = lastSnoozedAt; }

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    @Override
    public int compareTo(Reminder other) {
        int cmp = this.triggerTime.compareTo(other.triggerTime);
        return cmp != 0 ? cmp : this.id.compareTo(other.id);
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
                ", priority=" + priority +
                ", recurring=" + (recurrenceRule != null) +
                '}';
    }
}
