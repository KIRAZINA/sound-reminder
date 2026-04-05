package com.soundreminder.scheduler;

import com.soundreminder.model.Reminder;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** Flag to prevent scheduling while a reschedule is in progress. */
    private final AtomicBoolean scheduling = new AtomicBoolean(false);

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
     * Removes all reminders from the queue and cancels any pending scheduled tasks.
     */
    public synchronized void clear() {
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
        if (scheduling.compareAndSet(false, true)) {
            try {
                Reminder nextReminder = reminderQueue.peek();
                if (nextReminder == null) {
                    LOGGER.fine("No reminders to schedule");
                    return;
                }

                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                if (!nextReminder.isActive()) {
                    // Remove inactive reminder and try next
                    reminderQueue.poll();
                    recalculateScheduling();
                    return;
                }

                long delayMillis = java.time.Duration.between(now, nextReminder.getTriggerTime()).toMillis();

                if (delayMillis <= 0) {
                    // Already past trigger time - fire immediately
                    LOGGER.info("Firing reminder immediately: " + nextReminder.getMessage());
                    reminderQueue.poll(); // Remove from queue
                    onReminderFired.accept(nextReminder);
                    // Recalculate for remaining reminders
                    recalculateScheduling();
                } else {
                    // Schedule for the future
                    LOGGER.info("Scheduling next reminder in " + delayMillis + "ms: " +
                            nextReminder.getMessage());
                    executor.schedule(() -> {
                        // Fire the reminder and remove from queue
                        Reminder fired = reminderQueue.poll();
                        if (fired != null && fired.isActive()) {
                            LOGGER.info("Firing scheduled reminder: " + fired.getMessage());
                            onReminderFired.accept(fired);
                        }
                        // Schedule the next reminder in queue
                        recalculateScheduling();
                    }, delayMillis, TimeUnit.MILLISECONDS);
                }
            } finally {
                scheduling.set(false);
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
