package com.soundreminder.util;

import com.soundreminder.model.ParsedInput;
import com.soundreminder.model.Priority;
import com.soundreminder.model.RecurrenceRule;
import com.soundreminder.model.RecurrenceRule.Frequency;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based natural language parser for reminder input.
 * Supports relative time, absolute time, recurrence keywords, countdown shorthand, and priority tags.
 * All parsing is offline; no external AI or library dependencies.
 */
public class NaturalLanguageParser {

    private static final Pattern COUNTDOWN_SHORTHAND = Pattern.compile(
            "^(\\d+h)?\\s*(\\d+m)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern COUNTDOWN_FULL = Pattern.compile(
            "in\\s+(\\d+)\\s*(h(?:ours?)?|m(?:in(?:utes?)?)?|s(?:ec(?:onds?)?)?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RELATIVE_TIME = Pattern.compile(
            "in\\s+(\\d+)\\s*(h(?:ours?)?|m(?:in(?:utes?)?)?|s(?:ec(?:onds?)?)?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TOMORROW_TIME = Pattern.compile(
            "tomorrow\\s+at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NEXT_DAY = Pattern.compile(
            "next\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\s+at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ABSOLUTE_TIME = Pattern.compile(
            "at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ABSOLUTE_DATE_TIME = Pattern.compile(
            "on\\s+(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\s+at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RECURRENCE_DAILY = Pattern.compile(
            "\\bevery\\s+day\\b|\\bdaily\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern RECURRENCE_WEEKDAYS = Pattern.compile(
            "\\b(every\\s+)?weekday\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern RECURRENCE_WEEKLY = Pattern.compile(
            "\\bweekly\\b|\\bevery\\s+week\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern RECURRENCE_WEEKDAY_NAMED = Pattern.compile(
            "every\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RECURRENCE_MONTHLY = Pattern.compile(
            "\\bmonthly\\b|\\bevery\\s+month\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern RECURRENCE_YEARLY = Pattern.compile(
            "\\byearly\\b|\\bevery\\s+year\\b|\\bannually\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRIORITY_TAG = Pattern.compile(
            "!(high|urgent|normal|low)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern INTERVAL_MODIFIER = Pattern.compile(
            "every\\s+(\\d+)\\s+(day|days|week|weeks|month|months|year|years)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TIME_PATTERN_TWELVE = Pattern.compile(
            "^(\\d{1,2}):(\\d{2})\\s*(am|pm)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TIME_PATTERN_TWELVE_NOMIN = Pattern.compile(
            "^(\\d{1,2})\\s*(am|pm)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern TIME_PATTERN_24H = Pattern.compile(
            "^(\\d{1,2}):(\\d{2})$");

    private static final Set<String> DAY_NAMES = Set.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ParsedInput parse(String input) {
        ParsedInput result = new ParsedInput();
        if (input == null || input.isBlank()) {
            result.setError("Empty input");
            return result;
        }

        String remaining = input.trim();

        // Extract priority tag
        remaining = extractPriority(remaining, result);

        // Try countdown shorthand first (e.g., "20m meeting" or "1h30m")
        if (tryParseCountdownShorthand(remaining, result)) {
            return result;
        }

        // Extract countdown phrase (e.g., "in 20 minutes")
        remaining = extractCountdown(remaining, result);

        // Extract recurrence
        remaining = extractRecurrence(remaining, result);

        // Extract absolute date+time (e.g., "on 2026-06-25 at 14:00")
        remaining = extractAbsoluteDateTime(remaining, result);

        // Extract absolute time (e.g., "at 3:30pm")
        remaining = extractAbsoluteTime(remaining, result);

        // Extract relative time (e.g., "in 2 hours")
        remaining = extractRelativeTime(remaining, result);

        // Extract "tomorrow at X"
        remaining = extractTomorrowTime(remaining, result);

        // Extract "next monday at X"
        remaining = extractNextDayTime(remaining, result);

        // Clean up remaining text as the message
        result.setMessage(remaining.trim());

        return result;
    }

    private String extractPriority(String input, ParsedInput result) {
        Matcher m = PRIORITY_TAG.matcher(input);
        if (m.find()) {
            String tag = m.group(1).toLowerCase();
            switch (tag) {
                case "high" -> result.setPriority(Priority.HIGH);
                case "urgent" -> result.setPriority(Priority.URGENT);
                case "low" -> result.setPriority(Priority.LOW);
                default -> result.setPriority(Priority.NORMAL);
            }
            return m.replaceAll("").trim();
        }
        return input;
    }

    private boolean tryParseCountdownShorthand(String input, ParsedInput result) {
        if (input == null || input.isBlank()) return false;
        String[] parts = input.split("\\s+", 2);
        String firstToken = parts[0];

        Matcher m = COUNTDOWN_SHORTHAND.matcher(firstToken);
        if (m.matches()) {
            long totalSeconds = 0;
            String hoursStr = m.group(1);
            String minsStr = m.group(2);
            if (hoursStr != null) {
                totalSeconds += Long.parseLong(hoursStr.replaceAll("[hH]", "")) * 3600;
            }
            if (minsStr != null) {
                totalSeconds += Long.parseLong(minsStr.replaceAll("[mM]", "")) * 60;
            }
            if (totalSeconds > 0) {
                result.setCountdown(true);
                result.setCountdownSeconds(totalSeconds);
                result.setMessage(parts.length > 1 ? parts[1] : "Timer");
                return true;
            }
        }
        return false;
    }

    private String extractCountdown(String input, ParsedInput result) {
        if (result.isCountdown()) return input;
        Matcher m = COUNTDOWN_FULL.matcher(input);
        if (m.find()) {
            long amount = Long.parseLong(m.group(1));
            String unit = m.group(2).toLowerCase();
            long seconds = switch (unit.charAt(0)) {
                case 'h' -> amount * 3600;
                case 'm' -> amount * 60;
                case 's' -> amount;
                default -> 0;
            };
            if (seconds > 0) {
                result.setCountdown(true);
                result.setCountdownSeconds(seconds);
                return m.replaceAll("").trim();
            }
        }
        return input;
    }

    private String extractRelativeTime(String input, ParsedInput result) {
        if (result.getTriggerDateTime() != null) return input;
        Matcher m = RELATIVE_TIME.matcher(input);
        if (m.find()) {
            long amount = Long.parseLong(m.group(1));
            String unit = m.group(2).toLowerCase();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime trigger = switch (unit.charAt(0)) {
                case 'h' -> now.plusHours(amount);
                case 'm' -> now.plusMinutes(amount);
                case 's' -> now.plusSeconds(amount);
                default -> now;
            };
            result.setTriggerDateTime(trigger);
            return m.replaceAll("").trim();
        }
        return input;
    }

    private String extractAbsoluteDateTime(String input, ParsedInput result) {
        if (result.getTriggerDateTime() != null) return input;
        Matcher m = ABSOLUTE_DATE_TIME.matcher(input);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            int hour = Integer.parseInt(m.group(4));
            int minute = m.group(5) != null ? Integer.parseInt(m.group(5)) : 0;
            String ampm = m.group(6);

            if (ampm != null) {
                int[] h = adjustAmPm(hour, ampm);
                hour = h[0];
            }

            try {
                result.setTriggerDateTime(LocalDateTime.of(year, month, day, hour, minute));
            } catch (Exception e) {
                return input;
            }
            return m.replaceAll("").trim();
        }
        return input;
    }

    private String extractAbsoluteTime(String input, ParsedInput result) {
        if (result.getTriggerDateTime() != null) return input;
        Matcher m = ABSOLUTE_TIME.matcher(input);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            String ampm = m.group(3);

            if (ampm != null) {
                int[] h = adjustAmPm(hour, ampm);
                hour = h[0];
            }

            // Set to today; if time has passed, use tomorrow
            LocalDate today = LocalDate.now();
            LocalDateTime candidate = LocalDateTime.of(today, LocalTime.of(hour, minute));
            if (candidate.isBefore(LocalDateTime.now())) {
                candidate = candidate.plusDays(1);
            }
            result.setTriggerDateTime(candidate);
            return m.replaceAll("").trim();
        }
        return input;
    }

    private String extractTomorrowTime(String input, ParsedInput result) {
        if (result.getTriggerDateTime() != null) return input;
        Matcher m = TOMORROW_TIME.matcher(input);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            String ampm = m.group(3);

            if (ampm != null) {
                int[] h = adjustAmPm(hour, ampm);
                hour = h[0];
            }

            result.setTriggerDateTime(LocalDateTime.of(
                    LocalDate.now().plusDays(1), LocalTime.of(hour, minute)));
            return m.replaceAll("").trim();
        }
        return input;
    }

    private String extractNextDayTime(String input, ParsedInput result) {
        if (result.getTriggerDateTime() != null) return input;
        Matcher m = NEXT_DAY.matcher(input);
        if (m.find()) {
            String dayName = m.group(1).toLowerCase();
            int hour = Integer.parseInt(m.group(2));
            int minute = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
            String ampm = m.group(4);

            if (ampm != null) {
                int[] h = adjustAmPm(hour, ampm);
                hour = h[0];
            }

            DayOfWeek targetDay = parseDayOfWeek(dayName);
            if (targetDay != null) {
                LocalDate next = findNextDay(targetDay);
                result.setTriggerDateTime(LocalDateTime.of(next, LocalTime.of(hour, minute)));
                return m.replaceAll("").trim();
            }
        }
        return input;
    }

    private String extractRecurrence(String input, ParsedInput result) {
        RecurrenceRule rule = new RecurrenceRule();
        boolean hasRecurrence = false;
        String remaining = input;

        // Check interval modifier first (e.g., "every 2 days", "every 3 weeks")
        Matcher intervalMatcher = INTERVAL_MODIFIER.matcher(remaining);
        if (intervalMatcher.find()) {
            int interval = Integer.parseInt(intervalMatcher.group(1));
            String unit = intervalMatcher.group(2).toLowerCase();
            rule.setInterval(interval);
            hasRecurrence = true;
            remaining = intervalMatcher.replaceAll("").trim();

            if (unit.startsWith("day")) {
                rule.setFrequency(Frequency.DAILY);
            } else if (unit.startsWith("week")) {
                rule.setFrequency(Frequency.WEEKLY);
            } else if (unit.startsWith("month")) {
                rule.setFrequency(Frequency.MONTHLY);
            } else if (unit.startsWith("year")) {
                rule.setFrequency(Frequency.YEARLY);
            }
        }

        // Check named weekday recurrence (e.g., "every monday")
        Matcher weekdayMatcher = RECURRENCE_WEEKDAY_NAMED.matcher(remaining);
        if (weekdayMatcher.find()) {
            rule.setFrequency(Frequency.WEEKLY);
            String dayName = weekdayMatcher.group(1).toLowerCase();
            DayOfWeek dow = parseDayOfWeek(dayName);
            if (dow != null) {
                Set<DayOfWeek> days = new HashSet<>();
                days.add(dow);
                rule.setDaysOfWeek(days);
            }
            if (!hasRecurrence) {
                rule.setInterval(1);
                hasRecurrence = true;
            }
            remaining = weekdayMatcher.replaceAll("").trim();
        }

        // Check weekday recurrence (every weekday)
        Matcher weekdaysMatcher = RECURRENCE_WEEKDAYS.matcher(remaining);
        if (weekdaysMatcher.find()) {
            rule.setFrequency(Frequency.WEEKLY);
            Set<DayOfWeek> weekdays = new HashSet<>();
            weekdays.add(DayOfWeek.MONDAY);
            weekdays.add(DayOfWeek.TUESDAY);
            weekdays.add(DayOfWeek.WEDNESDAY);
            weekdays.add(DayOfWeek.THURSDAY);
            weekdays.add(DayOfWeek.FRIDAY);
            rule.setDaysOfWeek(weekdays);
            if (!hasRecurrence) {
                rule.setInterval(1);
                hasRecurrence = true;
            }
            remaining = weekdaysMatcher.replaceAll("").trim();
        }

        // Check daily
        Matcher dailyMatcher = RECURRENCE_DAILY.matcher(remaining);
        if (!hasRecurrence && dailyMatcher.find()) {
            rule.setFrequency(Frequency.DAILY);
            rule.setInterval(1);
            hasRecurrence = true;
            remaining = dailyMatcher.replaceAll("").trim();
        }

        // Check weekly
        Matcher weeklyMatcher = RECURRENCE_WEEKLY.matcher(remaining);
        if (!hasRecurrence && weeklyMatcher.find()) {
            rule.setFrequency(Frequency.WEEKLY);
            rule.setInterval(1);
            hasRecurrence = true;
            remaining = weeklyMatcher.replaceAll("").trim();
        }

        // Check monthly
        Matcher monthlyMatcher = RECURRENCE_MONTHLY.matcher(remaining);
        if (!hasRecurrence && monthlyMatcher.find()) {
            rule.setFrequency(Frequency.MONTHLY);
            rule.setInterval(1);
            hasRecurrence = true;
            remaining = monthlyMatcher.replaceAll("").trim();
        }

        // Check yearly
        Matcher yearlyMatcher = RECURRENCE_YEARLY.matcher(remaining);
        if (!hasRecurrence && yearlyMatcher.find()) {
            rule.setFrequency(Frequency.YEARLY);
            rule.setInterval(1);
            hasRecurrence = true;
            remaining = yearlyMatcher.replaceAll("").trim();
        }

        if (hasRecurrence) {
            rule.setDescription(rule.getFrequency().name().toLowerCase() +
                    (rule.getInterval() > 1 ? " every " + rule.getInterval() : ""));
            if (!rule.getDaysOfWeek().isEmpty()) {
                rule.setDescription(rule.getDescription() + " on " + rule.getDaysOfWeek());
            }
            result.setRecurrenceRule(rule);
        }

        return remaining;
    }

    private int[] adjustAmPm(int hour, String ampm) {
        String ampmLower = ampm.toLowerCase(Locale.ROOT);
        if (ampmLower.equals("pm") && hour != 12) {
            hour += 12;
        } else if (ampmLower.equals("am") && hour == 12) {
            hour = 0;
        }
        return new int[]{hour, 0};
    }

    private DayOfWeek parseDayOfWeek(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "monday" -> DayOfWeek.MONDAY;
            case "tuesday" -> DayOfWeek.TUESDAY;
            case "wednesday" -> DayOfWeek.WEDNESDAY;
            case "thursday" -> DayOfWeek.THURSDAY;
            case "friday" -> DayOfWeek.FRIDAY;
            case "saturday" -> DayOfWeek.SATURDAY;
            case "sunday" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private LocalDate findNextDay(DayOfWeek targetDay) {
        LocalDate today = LocalDate.now();
        LocalDate next = today;
        for (int i = 0; i <= 7; i++) {
            next = today.plusDays(i);
            if (next.getDayOfWeek() == targetDay) {
                return next;
            }
        }
        return today.plusDays(1);
    }
}
