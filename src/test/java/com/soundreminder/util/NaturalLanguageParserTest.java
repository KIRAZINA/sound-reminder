package com.soundreminder.util;

import com.soundreminder.model.ParsedInput;
import com.soundreminder.model.Priority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class NaturalLanguageParserTest {

    private final NaturalLanguageParser parser = new NaturalLanguageParser();

    @Test
    @DisplayName("Parses relative time 'in 2 hours' as countdown")
    void relativeTime() {
        ParsedInput p = parser.parse("in 2 hours meeting");
        assertFalse(p.hasError());
        assertTrue(p.isCountdown());
        assertEquals(7200, p.getCountdownSeconds());
        assertEquals("meeting", p.getMessage());
    }

    @Test
    @DisplayName("Parses countdown shorthand '20m'")
    void countdownShorthand() {
        ParsedInput p = parser.parse("20m coffee break");
        assertTrue(p.isCountdown());
        assertEquals(1200, p.getCountdownSeconds());
        assertEquals("coffee break", p.getMessage());
    }

    @Test
    @DisplayName("Parses absolute 'at 3:30pm'")
    void absoluteTime() {
        ParsedInput p = parser.parse("at 3:30pm dentist");
        assertFalse(p.hasError());
        assertNotNull(p.getTriggerDateTime());
        assertEquals("dentist", p.getMessage());
    }

    @Test
    @DisplayName("Parses tomorrow")
    void tomorrow() {
        ParsedInput p = parser.parse("tomorrow at 9am standup");
        assertFalse(p.hasError());
        assertNotNull(p.getTriggerDateTime());
    }

    @Test
    @DisplayName("Parses priority tag")
    void priority() {
        ParsedInput p = parser.parse("in 10 minutes !urgent task");
        assertEquals(Priority.URGENT, p.getPriority());
        assertEquals("task", p.getMessage());
    }

    @Test
    @DisplayName("Parses recurrence daily")
    void recurrenceDaily() {
        ParsedInput p = parser.parse("every day in 5 minutes check");
        assertNotNull(p.getRecurrenceRule());
        assertEquals(com.soundreminder.model.RecurrenceRule.Frequency.DAILY, p.getRecurrenceRule().getFrequency());
    }

    @Test
    @DisplayName("Parses every monday")
    void recurrenceWeeklyNamed() {
        ParsedInput p = parser.parse("every monday at 9am weekly sync");
        assertNotNull(p.getRecurrenceRule());
        assertEquals(com.soundreminder.model.RecurrenceRule.Frequency.WEEKLY, p.getRecurrenceRule().getFrequency());
    }

    @Test
    @DisplayName("Empty input yields error")
    void empty() {
        ParsedInput p = parser.parse("");
        assertTrue(p.hasError());
    }

    @Test
    @DisplayName("Plain text without time yields no trigger")
    void plainText() {
        ParsedInput p = parser.parse("just a note");
        assertFalse(p.hasError());
        assertNull(p.getTriggerDateTime());
        assertFalse(p.isCountdown());
        assertEquals("just a note", p.getMessage());
    }
}
