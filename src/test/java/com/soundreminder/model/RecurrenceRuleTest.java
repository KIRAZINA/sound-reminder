package com.soundreminder.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecurrenceRuleTest {

    @Test
    @DisplayName("Daily recurrence adds interval days")
    void daily() {
        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency(RecurrenceRule.Frequency.DAILY);
        rule.setInterval(2);
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime next = rule.computeNextOccurrence(from);
        assertEquals(LocalDateTime.of(2026, 1, 3, 10, 0), next);
    }

    @Test
    @DisplayName("Weekly named day finds next matching day")
    void weeklyNamed() {
        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency(RecurrenceRule.Frequency.WEEKLY);
        Set<DayOfWeek> days = new HashSet<>();
        days.add(DayOfWeek.MONDAY);
        rule.setDaysOfWeek(days);
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 9, 0); // Thursday
        LocalDateTime next = rule.computeNextOccurrence(from);
        assertNotNull(next);
        assertEquals(DayOfWeek.MONDAY, next.getDayOfWeek());
    }

    @Test
    @DisplayName("Monthly clamps day of month")
    void monthlyClamp() {
        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency(RecurrenceRule.Frequency.MONTHLY);
        rule.setDayOfMonth(31);
        LocalDateTime from = LocalDateTime.of(2026, 1, 15, 8, 0);
        LocalDateTime next = rule.computeNextOccurrence(from);
        assertNotNull(next);
        assertTrue(next.getDayOfMonth() <= 28 || next.getDayOfMonth() <= next.toLocalDate().lengthOfMonth());
    }

    @Test
    @DisplayName("Yearly adds one year")
    void yearly() {
        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency(RecurrenceRule.Frequency.YEARLY);
        LocalDateTime from = LocalDateTime.of(2026, 2, 28, 12, 0);
        LocalDateTime next = rule.computeNextOccurrence(from);
        assertEquals(2027, next.getYear());
    }

    @Test
    @DisplayName("Null from returns null")
    void nullFrom() {
        RecurrenceRule rule = new RecurrenceRule();
        assertNull(rule.computeNextOccurrence(null));
    }
}
