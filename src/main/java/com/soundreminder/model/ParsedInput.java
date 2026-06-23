package com.soundreminder.model;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Output model for the NLP parser.
 * Represents a parsed user input string with all resolved fields.
 */
public class ParsedInput {

    private String message;
    private LocalDateTime triggerDateTime;
    private long countdownSeconds;
    private boolean isCountdown;
    private RecurrenceRule recurrenceRule;
    private Priority priority;
    private String error;

    public ParsedInput() {
        this.countdownSeconds = -1;
        this.isCountdown = false;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTriggerDateTime() { return triggerDateTime; }
    public void setTriggerDateTime(LocalDateTime triggerDateTime) { this.triggerDateTime = triggerDateTime; }

    public long getCountdownSeconds() { return countdownSeconds; }
    public void setCountdownSeconds(long countdownSeconds) { this.countdownSeconds = countdownSeconds; }

    public boolean isCountdown() { return isCountdown; }
    public void setCountdown(boolean countdown) { isCountdown = countdown; }

    public RecurrenceRule getRecurrenceRule() { return recurrenceRule; }
    public void setRecurrenceRule(RecurrenceRule recurrenceRule) { this.recurrenceRule = recurrenceRule; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public boolean hasError() { return error != null && !error.isBlank(); }
}
