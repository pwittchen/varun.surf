package com.github.pwittchen.varun.model.filter;

import com.github.pwittchen.varun.model.currentconditions.CurrentConditions;
import com.github.pwittchen.varun.model.currentconditions.filter.CurrentConditionsEmptyFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentConditionsEmptyFilterTest {

    @Test
    void shouldReturnTrueWhenCurrentConditionsIsNull() {
        // given
        CurrentConditions conditions = null;

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnTrueWhenAllFieldsAreNullOrZero() {
        // given
        var conditions = new CurrentConditions(null, 0, 0, null, 0);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenDateIsNotNull() {
        // given
        var conditions = new CurrentConditions("2025-01-01 12:00", 0, 0, null, 0);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenDirectionIsNotNull() {
        // given
        var conditions = new CurrentConditions(null, 0, 0, "N", 0);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenWindIsNotZero() {
        // given
        var conditions = new CurrentConditions(null, 15, 0, null, 0);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenGustsIsNotZero() {
        // given
        var conditions = new CurrentConditions(null, 0, 20, null, 0);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenTempIsNotZero() {
        // given
        var conditions = new CurrentConditions(null, 0, 0, null, 25);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenAllFieldsHaveValues() {
        // given
        var conditions = new CurrentConditions("2025-01-01 12:00", 15, 20, "NW", 25);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenSomeFieldsHaveValues() {
        // given
        var conditions = new CurrentConditions("2025-01-01 12:00", 15, 0, null, 0);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenOnlyWindHasValue() {
        // given
        var conditions = new CurrentConditions(null, 10, 0, null, 0);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenOnlyGustsHasValue() {
        // given
        var conditions = new CurrentConditions(null, 0, 15, null, 0);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenOnlyTempHasValue() {
        // given
        var conditions = new CurrentConditions(null, 0, 0, null, 20);

        // when
        var result = CurrentConditionsEmptyFilter.isEmpty(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void equalsMethodShouldReturnTrueForEmptyConditions() {
        // given
        var filter = new CurrentConditionsEmptyFilter();
        var conditions = new CurrentConditions(null, 0, 0, null, 0);

        // when
        var result = filter.equals(conditions);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void equalsMethodShouldReturnFalseForNonEmptyConditions() {
        // given
        var filter = new CurrentConditionsEmptyFilter();
        var conditions = new CurrentConditions("2025-01-01", 15, 20, "N", 25);

        // when
        var result = filter.equals(conditions);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void equalsMethodShouldReturnFalseForNonCurrentConditionsObject() {
        // given
        var filter = new CurrentConditionsEmptyFilter();
        var obj = "not a CurrentConditions object";

        // when
        var result = filter.equals(obj);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void equalsMethodShouldReturnFalseForNull() {
        // given
        var filter = new CurrentConditionsEmptyFilter();

        // when
        var result = filter.equals(null);

        // then
        assertThat(result).isFalse();
    }
}