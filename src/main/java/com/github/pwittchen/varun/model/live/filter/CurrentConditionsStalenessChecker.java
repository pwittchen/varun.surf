package com.github.pwittchen.varun.model.live.filter;

import com.github.pwittchen.varun.model.live.CurrentConditions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class CurrentConditionsStalenessChecker {

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    );

    private CurrentConditionsStalenessChecker() {
    }

    public static boolean isStale(CurrentConditions conditions, Clock clock) {
        if (conditions == null || conditions.date() == null || conditions.date().isBlank()) {
            return true;
        }

        LocalDateTime readingTime = parse(conditions.date().trim());
        if (readingTime == null) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        long minutesAge = ChronoUnit.MINUTES.between(readingTime, now);
        return minutesAge >= 60;
    }

    private static LocalDateTime parse(String date) {
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(date, formatter);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
