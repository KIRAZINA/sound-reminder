package com.soundreminder.scheduler;

import com.soundreminder.model.Reminder;
import com.soundreminder.model.ReminderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReminderScheduler.
 * Tests scheduling logic, overdue detection, and firing callbacks.
 */
class SchedulerTest {

    private ScheduledExecutorService executorService;
    private ReminderScheduler scheduler;
    private CopyOnWriteArrayList<Reminder> firedReminders;

    @BeforeEach
    void setUp() {
        executorService = Executors.newScheduledThreadPool(2);
        firedReminders = new CopyOnWriteArrayList<>();

        scheduler = new ReminderScheduler(executorService, firedReminders::add);
    }

    @Test
    @DisplayName("Schedule a reminder that fires in the near future")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testScheduleFutureReminder() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Reminder> firedRef = new AtomicReference<>();

        // Create a custom scheduler that fires immediately for this test
        ReminderScheduler testScheduler = new ReminderScheduler(
                executorService,
                r -> {
                    firedRef.set(r);
                    latch.countDown();
                }
        );

        LocalDateTime triggerTime = LocalDateTime.now().plusSeconds(1);
        Reminder reminder = new Reminder(
                ReminderType.SCHEDULED,
                triggerTime,
                "Test reminder",
                null
        );

        testScheduler.schedule(reminder);

        // Wait for the reminder to fire (should be ~1 second)
        boolean fired = latch.await(5, TimeUnit.SECONDS);
        assertTrue(fired, "Reminder should have fired within 5 seconds");
        assertNotNull(firedRef.get());
        assertEquals(reminder.getId(), firedRef.get().getId());
    }

    @Test
    @DisplayName("Schedule a reminder that is already overdue fires immediately")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOverdueReminderFiresImmediately() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Reminder> firedRef = new AtomicReference<>();

        ReminderScheduler testScheduler = new ReminderScheduler(
                executorService,
                r -> {
                    firedRef.set(r);
                    latch.countDown();
                }
        );

        // Create a reminder in the past
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(5);
        Reminder reminder = new Reminder(
                ReminderType.SCHEDULED,
                pastTime,
                "Overdue reminder",
                null
        );

        testScheduler.schedule(reminder);

        // Should fire almost immediately
        boolean fired = latch.await(3, TimeUnit.SECONDS);
        assertTrue(fired, "Overdue reminder should fire immediately");
        assertNotNull(firedRef.get());
        assertEquals("Overdue reminder", firedRef.get().getMessage());
    }

    @Test
    @DisplayName("cancel removes a reminder from scheduling")
    void testCancelReminder() {
        Reminder reminder = new Reminder(
                ReminderType.SCHEDULED,
                LocalDateTime.now().plusHours(1),
                "To be cancelled",
                null
        );

        scheduler.schedule(reminder);
        assertEquals(1, scheduler.getPendingCount());

        scheduler.cancel(reminder.getId());
        assertEquals(0, scheduler.getPendingCount());
    }

    @Test
    @DisplayName("clear removes all reminders")
    void testClearAllReminders() {
        Reminder r1 = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.now().plusHours(1), "R1", null);
        Reminder r2 = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.now().plusHours(2), "R2", null);
        Reminder r3 = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.now().plusHours(3), "R3", null);

        scheduler.schedule(r1);
        scheduler.schedule(r2);
        scheduler.schedule(r3);

        assertEquals(3, scheduler.getPendingCount());

        scheduler.clear();
        assertEquals(0, scheduler.getPendingCount());
    }

    @Test
    @DisplayName("schedule ignores inactive reminders")
    void testScheduleInactiveReminder() {
        Reminder reminder = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.now().plusHours(1), "Inactive", null);
        reminder.setActive(false);

        scheduler.schedule(reminder);
        assertEquals(0, scheduler.getPendingCount());
    }

    @Test
    @DisplayName("getPendingCount returns correct count")
    void testGetPendingCount() {
        Reminder r1 = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.now().plusHours(1), "R1", null);
        Reminder r2 = new Reminder(ReminderType.SCHEDULED,
                LocalDateTime.now().plusHours(2), "R2", null);

        scheduler.schedule(r1);
        scheduler.schedule(r2);

        assertEquals(2, scheduler.getPendingCount());
    }

    @Test
    @DisplayName("Priority ordering: earlier triggerTime fires first")
    void testPriorityOrdering() {
        LocalDateTime base = LocalDateTime.now().plusHours(1);
        Reminder earlier = new Reminder(ReminderType.SCHEDULED, base, "Earlier", null);
        Reminder later = new Reminder(ReminderType.SCHEDULED, base.plusMinutes(30), "Later", null);

        // Reminder implements Comparable by triggerTime
        assertTrue(earlier.compareTo(later) < 0);
    }

    @Test
    @DisplayName("COUNTDOWN type reminder can be scheduled")
    void testCountdownTypeScheduling() {
        Reminder reminder = new Reminder(
                ReminderType.COUNTDOWN,
                LocalDateTime.now().plusSeconds(2),
                "Countdown timer",
                null
        );

        scheduler.schedule(reminder);
        assertEquals(1, scheduler.getPendingCount());
    }
}
