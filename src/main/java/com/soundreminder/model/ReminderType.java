package com.soundreminder.model;

/**
 * Enumeration of reminder types.
 * SCHEDULED: a specific date/time reminder.
 * COUNTDOWN: a countdown timer from the current time.
 */
public enum ReminderType {
    /** A reminder scheduled for a specific date and time. */
    SCHEDULED,
    /** A countdown timer with a specified duration. */
    COUNTDOWN
}
