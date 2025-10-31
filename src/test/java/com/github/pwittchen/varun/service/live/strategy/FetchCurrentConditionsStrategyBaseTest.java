package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class FetchCurrentConditionsStrategyBaseTest {

    private final TestFetchCurrentConditionsStrategy strategy = new TestFetchCurrentConditionsStrategy();

    @ParameterizedTest
    @CsvSource({
            "N, N",
            "NE, NE",
            "E, E",
            "SE, SE",
            "S, S",
            "SW, SW",
            "W, W",
            "NW, NW"
    })
    void shouldReturnSameDirectionWhenAlreadyInWindDirections(String input, String expected) {
        assertThat(strategy.normalizeDirection(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "NNE, NE",
            "ENE, NE",
            "ESE, SE",
            "SSE, SE",
            "SSW, SW",
            "WSW, SW",
            "WNW, NW",
            "NNW, NW"
    })
    void shouldNormalizeIntermediateDirections(String input, String expected) {
        assertThat(strategy.normalizeDirection(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "nne, NE",
            "ene, NE",
            "ese, SE",
            "sse, SE",
            "ssw, SW",
            "wsw, SW",
            "wnw, NW",
            "nnw, NW"
    })
    void shouldHandleLowercaseDirections(String input, String expected) {
        assertThat(strategy.normalizeDirection(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "NnE, NE",
            "EnE, NE",
            "SsW, SW"
    })
    void shouldHandleMixedCaseDirections(String input, String expected) {
        assertThat(strategy.normalizeDirection(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "North, N",
            "East, E",
            "South, S",
            "West, W"
    })
    void shouldFindClosestDirectionForLongFormDirections(String input, String expected) {
        assertThat(strategy.normalizeDirection(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Unknown", "Invalid", "XYZ", ""})
    void shouldReturnNForUnknownDirections(String input) {
        assertThat(strategy.normalizeDirection(input)).isEqualTo("N");
    }

    @ParameterizedTest
    @CsvSource({
            "NORTH, N",
            "EAST, E",
            "SOUTH, S",
            "WEST, W"
    })
    void shouldFindClosestDirectionWhenStartsWith(String input, String expected) {
        assertThat(strategy.findClosestDirection(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Unknown", "Random"})
    void shouldReturnNForUnmatchedDirectionInFindClosest(String input) {
        assertThat(strategy.findClosestDirection(input)).isEqualTo("N");
    }

    @ParameterizedTest
    @CsvSource({
            "N-NE, N"
    })
    void shouldHandleDirectionsWithExtraCharacters(String input, String expected) {
        assertThat(strategy.normalizeDirection(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "north, N",
            "sOuTh, S"
    })
    void shouldHandleCaseInsensitiveMatching(String input, String expected) {
        assertThat(strategy.normalizeDirection(input)).isEqualTo(expected);
    }

    // Test implementation to expose protected methods
    private static class TestFetchCurrentConditionsStrategy extends FetchCurrentConditionsStrategyBase {
        @Override
        protected Mono<CurrentConditions> fetchCurrentConditions(String url) {
            return Mono.empty();
        }

        @Override
        protected String getUrl(int wgId) {
            return "";
        }

        // Expose protected methods for testing
        @Override
        public String normalizeDirection(String rawDirection) {
            return super.normalizeDirection(rawDirection);
        }

        @Override
        public String findClosestDirection(String rawDirection) {
            return super.findClosestDirection(rawDirection);
        }
    }
}