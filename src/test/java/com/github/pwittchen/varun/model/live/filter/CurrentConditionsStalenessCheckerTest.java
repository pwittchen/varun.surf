package com.github.pwittchen.varun.model.live.filter;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.google.common.truth.Truth.assertThat;

class CurrentConditionsStalenessCheckerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final LocalDateTime NOW = LocalDateTime.of(2025, 6, 15, 14, 0, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            NOW.atZone(ZONE).toInstant(), ZONE
    );

    private CurrentConditions conditions(String date) {
        return new CurrentConditions(date, 15, 20, "N", 22);
    }

    // --- Format: yyyy-MM-dd HH:mm:ss ---

    @Test
    void shouldReturnFalseForFreshReadingWithSecondsFormat() {
        String date = NOW.minusMinutes(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions(date), FIXED_CLOCK)).isFalse();
    }

    @Test
    void shouldReturnTrueForStaleReadingWithSecondsFormat() {
        String date = NOW.minusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions(date), FIXED_CLOCK)).isTrue();
    }

    // --- Format: yyyy-MM-dd HH:mm ---

    @Test
    void shouldReturnFalseForFreshReadingWithMinutesFormat() {
        String date = NOW.minusMinutes(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions(date), FIXED_CLOCK)).isFalse();
    }

    @Test
    void shouldReturnTrueForStaleReadingWithMinutesFormat() {
        String date = NOW.minusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions(date), FIXED_CLOCK)).isTrue();
    }

    // --- Format: dd.MM.yyyy HH:mm ---

    @Test
    void shouldReturnFalseForFreshReadingWithDottedFormat() {
        String date = NOW.minusMinutes(30).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions(date), FIXED_CLOCK)).isFalse();
    }

    @Test
    void shouldReturnTrueForStaleReadingWithDottedFormat() {
        String date = NOW.minusHours(2).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions(date), FIXED_CLOCK)).isTrue();
    }

    // --- Boundary tests ---

    @Test
    void shouldReturnTrueForExactlyOneHourOld() {
        String date = NOW.minusMinutes(60).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions(date), FIXED_CLOCK)).isTrue();
    }

    @Test
    void shouldReturnFalseForFiftyNineMinutesOld() {
        String date = NOW.minusMinutes(59).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions(date), FIXED_CLOCK)).isFalse();
    }

    // --- Null / blank / unparseable ---

    @Test
    void shouldReturnTrueForNullConditions() {
        assertThat(CurrentConditionsStalenessChecker.isStale(null, FIXED_CLOCK)).isTrue();
    }

    @Test
    void shouldReturnTrueForNullDate() {
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions(null), FIXED_CLOCK)).isTrue();
    }

    @Test
    void shouldReturnTrueForBlankDate() {
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions("   "), FIXED_CLOCK)).isTrue();
    }

    @Test
    void shouldReturnTrueForUnparseableDate() {
        assertThat(CurrentConditionsStalenessChecker.isStale(conditions("not-a-date"), FIXED_CLOCK)).isTrue();
    }
}
