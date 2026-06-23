package com.soundreminder.scheduler;

import com.soundreminder.model.Reminder;

import java.time.LocalDateTime;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages precise scheduling of reminders using a ScheduledExecutorService
 * and a PriorityBlockingQueue ordered by trigger time.
 * Fires a callback when a reminder's trigger time is reached.
 */
public class ReminderScheduler {

    private static final Logger LOGGER = Logger.getLogger(ReminderScheduler.class.getName());

    /** The thread pool executor for scheduling tasks. */
    private final ScheduledExecutorService executor;

    /** Priority queue holding all active reminders sorted by trigger time. */
    private final PriorityBlockingQueue<Reminder> reminderQueue;

    /** Callback invoked when a reminder fires. Receives the Reminder as argument. */
    private final Consumer<Reminder> onReminderFired;

    /** Lock to serialize concurrent scheduling recalculations. */
    private final ReentrantLock schedulingLock = new ReentrantLock();

    /** Currently scheduled future task, cancelled before scheduling a new one to prevent duplicates. */
    private ScheduledFuture<?> scheduledFuture;

    /**
     * Creates a new ReminderScheduler.
     *
     * @param executor         the ScheduledExecutorService to use for scheduling
     * @param onReminderFired  callback invoked when a reminder reaches its trigger time
     */
    public ReminderScheduler(ScheduledExecutorService executor, Consumer<Reminder> onReminderFired) {
        this.executor = executor;
        this.reminderQueue = new PriorityBlockingQueue<>();
        this.onReminderFired = onReminderFired;
    }

    /**
     * Adds a reminder to the scheduling queue and recalculates scheduling.
     *
     * @param reminder the reminder to schedule
     */
    public synchronized void schedule(Reminder reminder) {
        if (!reminder.isActive()) {
            LOGGER.warning("Attempted to schedule inactive reminder: " + reminder.getId());
            return;
        }
        reminderQueue.add(reminder);
        LOGGER.info("Scheduled reminder: " + reminder.getMessage() +
                " at " + reminder.getTriggerTime());
        recalculateScheduling();
    }

    /**
     * Removes a reminder from the scheduling queue.
     *
     * @param reminderId the ID of the reminder to remove
     */
    public synchronized void cancel(String reminderId) {
        reminderQueue.removeIf(r -> r.getId().equals(reminderId));
        LOGGER.info("Cancelled reminder: " + reminderId);
        // Recalculate to ensure the next scheduled reminder is correct
        recalculateScheduling();
    }

    /**
     * Replaces a reminder in the scheduling queue with an updated version (e.g., after snooze).
     * Removes the old entry, adds the updated one, and recalculates scheduling.
     *
     * @param reminder the updated reminder to re-schedule
     */
    public synchronized void reSchedule(Reminder reminder) {
        reminderQueue.removeIf(r -> r.getId().equals(reminder.getId()));
        if (reminder.isActive() && !reminder.isDone()) {
            reminderQueue.add(reminder);
            LOGGER.info("Re-scheduled reminder: " + reminder.getMessage() +
                    " at " + reminder.getTriggerTime());
        }
        recalculateScheduling();
    }

    /**
     * Removes all reminders from the queue and cancels any pending scheduled tasks.
     */
    public synchronized void clear() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
        reminderQueue.clear();
        LOGGER.info("Cleared all scheduled reminders");
    }

    /**
     * Checks for any reminders that should have already fired and fires them immediately.
     * Call this on startup to catch reminders that were due while the app was not running.
     */
    public synchronized void checkOverdueReminders() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        reminderQueue.removeIf(reminder -> {
            if (reminder.isActive() && !reminder.getTriggerTime().isAfter(now)) {
                LOGGER.info("Firing overdue reminder immediately: " + reminder.getMessage());
                onReminderFired.accept(reminder);
                return true; // Remove from queue
            }
            return false;
        });
    }

    /**
     * Recalculates which reminder should be scheduled next.
     * Only the earliest reminder (head of priority queue) is scheduled with the executor.
     * When it fires, this method is called again to schedule the next one.
     */
    private void recalculateScheduling() {
        schedulingLock.lock();
        try {
            // Cancel any previously scheduled task to prevent duplicates
            if (scheduledFuture != null && !scheduledFuture.isDone()) {
                scheduledFuture.cancel(false);
            }
            scheduledFuture = null;

            Reminder nextReminder = reminderQueue.peek();
            if (nextReminder == null) {
                LOGGER.fine("No reminders to schedule");
                return;
            }

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            if (!nextReminder.isActive()) {
                reminderQueue.poll();
                recalculateScheduling();
                return;
            }

            long delayMillis = java.time.Duration.between(now, nextReminder.getTriggerTime()).toMillis();

            if (delayMillis <= 0) {
                LOGGER.info("Firing reminder immediately: " + nextReminder.getMessage());
                reminderQueue.poll();
                onReminderFired.accept(nextReminder);
                rescheduleIfRecurring(nextReminder);
                recalculateScheduling();
            } else {
                LOGGER.info("Scheduling next reminder in " + delayMillis + "ms: " +
                        nextReminder.getMessage());
                scheduledFuture = executor.schedule(() -> {
                    Reminder fired = reminderQueue.poll();
                    if (fired != null && fired.isActive()) {
                        LOGGER.info("Firing scheduled reminder: " + fired.getMessage());
                        onReminderFired.accept(fired);
                        rescheduleIfRecurring(fired);
                    }
                    recalculateScheduling();
                }, delayMillis, TimeUnit.MILLISECONDS);
            }
        } finally {
            schedulingLock.unlock();
        }
    }

    /**
     * If the fired reminder is recurring and has not been manually deactivated,
     * computes its next occurrence and re-adds it to the scheduling queue.
     */
    private void rescheduleIfRecurring(Reminder fired) {
        if (fired.isRecurring() && !fired.isDone()) {
            fired.setLastFiredAt(java.time.LocalDateTime.now());
            LocalDateTime next = fired.computeNextOccurrence();
            if (next != null) {
                fired.setTriggerTime(next);
                fired.setActive(true);
                fired.setDone(false);
                reminderQueue.add(fired);
                LOGGER.info("Recurring reminder rescheduled: " + fired.getMessage() +
                        " next at " + next);
            }
        }
    }

    /**
     * Returns the number of currently scheduled (active) reminders.
     *
     * @return count of active reminders in the queue
     */
    public int getPendingCount() {
        return (int) reminderQueue.stream().filter(Reminder::isActive).count();
    }
}
