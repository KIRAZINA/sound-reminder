package com.soundreminder.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Reminder model class.
 */
class ReminderTest {

    @Test
    @DisplayName("Constructor sets all fields correctly")
    void testConstructorSetsFields() {
        LocalDateTime triggerTime = LocalDateTime.of(2026, 5, 1, 10, 30);
        Reminder reminder = new Reminder(
                ReminderType.SCHEDULED,
                triggerTime,
                "Test message",
                "/path/to/image.png"
        );

        assertNotNull(reminder.getId());
        assertEquals(ReminderType.SCHEDULED, reminder.getType());
        assertEquals(triggerTime, reminder.getTriggerTime());
        assertEquals("Test message", reminder.getMessage());
        assertEquals("/path/to/image.png", reminder.getImagePath());
        assertTrue(reminder.isActive());
        assertFalse(reminder.isDone());
        assertNotNull(reminder.getCreatedAt());
    }

    @Test
    @DisplayName("Constructor generates unique IDs")
    void testUniqueIds() {
        Reminder r1 = new Reminder(ReminderType.SCHEDULED, LocalDateTime.now(), "A", null);
        Reminder r2 = new Reminder(ReminderType.SCHEDULED, LocalDateTime.now(), "B", null);

        assertNotEquals(r1.getId(), r2.getId());
    }

    @Test
    @DisplayName("markAsDone sets done=true and active=false")
    void testMarkAsDone() {
        Reminder reminder = new Reminder(
                ReminderType.COUNTDOWN,
                LocalDateTime.now().plusMinutes(5),
                "Timer message",
                null
        );

        assertTrue(reminder.isActive());
        assertFalse(reminder.isDone());

        reminder.markAsDone();

        assertFalse(reminder.isActive());
        assertTrue(reminder.isDone());
    }

    @Test
    @DisplayName("Setters update fields correctly")
    void testSetters() {
        Reminder reminder = new Reminder(
                ReminderType.SCHEDULED,
                LocalDateTime.now(),
                "Original",
                null
        );

        reminder.setMessage("Updated message");
        assertEquals("Updated message", reminder.getMessage());

        reminder.setImagePath("/new/path.jpg");
        assertEquals("/new/path.jpg", reminder.getImagePath());

        LocalDateTime newTime = LocalDateTime.now().plusHours(1);
        reminder.setTriggerTime(newTime);
        assertEquals(newTime, reminder.getTriggerTime());

        reminder.setType(ReminderType.COUNTDOWN);
        assertEquals(ReminderType.COUNTDOWN, reminder.getType());

        reminder.setActive(false);
        assertFalse(reminder.isActive());
    }

    @Test
    @DisplayName("compareTo orders by triggerTime")
    void testCompareTo() {
        LocalDateTime base = LocalDateTime.now();
        Reminder earlier = new Reminder(ReminderType.SCHEDULED, base, "Earlier", null);
        Reminder later = new Reminder(ReminderType.SCHEDULED, base.plusMinutes(10), "Later", null);

        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);
        assertEquals(0, earlier.compareTo(earlier));
    }

    @Test
    @DisplayName("equals and hashCode based on ID")
    void testEqualsAndHashCode() {
        Reminder r1 = new Reminder(ReminderType.SCHEDULED, LocalDateTime.now(), "A", null);
        Reminder r2 = new Reminder(ReminderType.SCHEDULED, LocalDateTime.now().plusHours(1), "B", null);

        // Different IDs
        assertNotEquals(r1, r2);

        // Same ID (manually set)
        r2.setId(r1.getId());
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());

        // Null and different class
        assertNotEquals(r1, null);
        assertNotEquals(r1, "string");
        // Reflexive
        assertEquals(r1, r1);
    }

    @Test
    @DisplayName("toString contains key fields")
    void testToString() {
        Reminder reminder = new Reminder(
                ReminderType.COUNTDOWN,
                LocalDateTime.of(2026, 12, 25, 0, 0),
                "Christmas timer",
                null
        );

        String str = reminder.toString();
        assertTrue(str.contains("COUNTDOWN"));
        assertTrue(str.contains("Christmas timer"));
        assertTrue(str.contains(reminder.getId()));
    }

    @Test
    @DisplayName("COUNTDOWN type reminder works correctly")
    void testCountdownType() {
        Reminder reminder = new Reminder(
                ReminderType.COUNTDOWN,
                LocalDateTime.now().plusSeconds(60),
                "60 second timer",
                null
        );

        assertEquals(ReminderType.COUNTDOWN, reminder.getType());
        assertTrue(reminder.getTriggerTime().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Reminder with null image path")
    void testNullImagePath() {
        Reminder reminder = new Reminder(
                ReminderType.SCHEDULED,
                LocalDateTime.now(),
                "No image",
                null
        );

        assertNull(reminder.getImagePath());
    }
}
