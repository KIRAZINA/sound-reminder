package com.soundreminder.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

public class RecurrenceRule {

    public enum Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    private Frequency frequency;
    private int interval;
    private Set<DayOfWeek> daysOfWeek;
    private Integer dayOfMonth;
    private String description;

    public RecurrenceRule() {
        this.frequency = Frequency.DAILY;
        this.interval = 1;
        this.daysOfWeek = new HashSet<>();
        this.description = "";
    }

    @JsonCreator
    public RecurrenceRule(
            @JsonProperty("frequency") Frequency frequency,
            @JsonProperty("interval") int interval,
            @JsonProperty("daysOfWeek") Set<DayOfWeek> daysOfWeek,
            @JsonProperty("dayOfMonth") Integer dayOfMonth,
            @JsonProperty("description") String description) {
        this.frequency = frequency != null ? frequency : Frequency.DAILY;
        this.interval = Math.max(1, interval);
        this.daysOfWeek = daysOfWeek != null ? daysOfWeek : new HashSet<>();
        this.dayOfMonth = dayOfMonth;
        this.description = description != null ? description : "";
    }

    public LocalDateTime computeNextOccurrence(LocalDateTime from) {
        if (from == null) return null;
        LocalTime time = from.toLocalTime();
        LocalDate date = from.toLocalDate();
        LocalDate next;

        switch (frequency) {
            case DAILY:
                next = date.plusDays(interval);
                break;
            case WEEKLY:
                next = date;
                if (!daysOfWeek.isEmpty()) {
                    next = findNextDayOfWeek(date);
                } else {
                    next = date.plusWeeks(interval);
                }
                break;
            case MONTHLY:
                if (dayOfMonth != null) {
                    int targetDay = Math.min(dayOfMonth, date.lengthOfMonth());
                    next = date.withDayOfMonth(targetDay);
                    if (!next.isAfter(date)) {
                        next = date.plusMonths(interval).withDayOfMonth(
                                Math.min(dayOfMonth, date.plusMonths(interval).lengthOfMonth()));
                    }
                } else {
                    next = date.plusMonths(interval);
                }
                break;
            case YEARLY:
                next = date.plusYears(interval);
                break;
            default:
                next = date.plusDays(1);
        }

        if (next.equals(date) && !next.isAfter(date)) {
            next = next.plusDays(1);
        }

        return LocalDateTime.of(next, time);
    }

    private LocalDate findNextDayOfWeek(LocalDate from) {
        for (int i = 1; i <= 7; i++) {
            LocalDate candidate = from.plusDays(i);
            if (daysOfWeek.contains(candidate.getDayOfWeek())) {
                return candidate;
            }
        }
        return from.plusDays(1);
    }

    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }

    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = Math.max(1, interval); }

    public Set<DayOfWeek> getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(Set<DayOfWeek> daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "RecurrenceRule{" + description + "}";
    }
}
