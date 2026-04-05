package com.soundreminder.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ReminderType enum.
 */
class ReminderTypeTest {

    @Test
    @DisplayName("ReminderType has exactly two values")
    void testEnumValues() {
        ReminderType[] values = ReminderType.values();
        assertEquals(2, values.length);
    }

    @Test
    @DisplayName("SCHEDULED enum value exists")
    void testScheduledExists() {
        assertNotNull(ReminderType.valueOf("SCHEDULED"));
        assertEquals(ReminderType.SCHEDULED, ReminderType.valueOf("SCHEDULED"));
    }

    @Test
    @DisplayName("COUNTDOWN enum value exists")
    void testCountdownExists() {
        assertNotNull(ReminderType.valueOf("COUNTDOWN"));
        assertEquals(ReminderType.COUNTDOWN, ReminderType.valueOf("COUNTDOWN"));
    }

    @Test
    @DisplayName("valueOf throws on invalid input")
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () ->
                ReminderType.valueOf("INVALID"));
    }
}
