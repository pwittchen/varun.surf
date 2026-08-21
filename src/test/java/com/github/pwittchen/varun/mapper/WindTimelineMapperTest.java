package com.github.pwittchen.varun.mapper;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.WindTimeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

class WindTimelineMapperTest {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", Locale.ENGLISH);

    private static final LocalDateTime START = LocalDateTime.of(2025, 10, 28, 14, 0);

    private WindTimelineMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new WindTimelineMapper();
    }

    private Forecast forecastAt(LocalDateTime time, double wind, double gusts, String direction) {
        return new Forecast(time.format(FORMATTER), wind, gusts, direction, 15, 0, 0, 1013);
    }

    @Test
    void shouldBuildHourlyGridOfRequestedLength() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(forecastAt(START, 12, 16, "NW"))), START, 4);

        assertThat(timeline.hours()).hasSize(4);
        assertThat(timeline.hours().getFirst()).isEqualTo("Tue 28 Oct 2025 14:00");
        assertThat(timeline.hours().getLast()).isEqualTo("Tue 28 Oct 2025 17:00");
    }

    @Test
    void shouldPlaceForecastsAtTheirHourOnTheGrid() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(
                        forecastAt(START, 12, 16, "NW"),
                        forecastAt(START.plusHours(1), 20, 26, "SW")
                )), START, 2);

        WindTimeline.SpotWind spot = timeline.spots().getFirst();
        assertThat(spot.wgId()).isEqualTo(1);
        assertThat(spot.wind()).containsExactly(12, 20).inOrder();
        assertThat(spot.gusts()).containsExactly(16, 26).inOrder();
        // NW and SW are indices 7 and 5 of the cardinal list
        assertThat(spot.direction()).containsExactly(7, 5).inOrder();
    }

    @Test
    void shouldHoldSamplesForwardAcrossThreeHourlySteps() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(
                        forecastAt(START, 12, 16, "NW"),
                        forecastAt(START.plusHours(3), 20, 26, "SW")
                )), START, 6);

        WindTimeline.SpotWind spot = timeline.spots().getFirst();
        assertThat(spot.wind()).containsExactly(12, 12, 12, 20, 20, 20).inOrder();
        assertThat(spot.gusts()).containsExactly(16, 16, 16, 26, 26, 26).inOrder();
        assertThat(spot.direction()).containsExactly(7, 7, 7, 5, 5, 5).inOrder();
    }

    @Test
    void shouldLeaveGapsWiderThanTheStrideEmpty() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(
                        forecastAt(START, 12, 16, "NW"),
                        forecastAt(START.plusHours(5), 20, 26, "SW")
                )), START, 6);

        WindTimeline.SpotWind spot = timeline.spots().getFirst();
        assertThat(spot.wind()).containsExactly(12, 12, 12, null, null, 20).inOrder();
    }

    @Test
    void shouldStartTheGridOnAWholeHour() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(forecastAt(START, 12, 16, "N"))), START.plusMinutes(37), 2);

        assertThat(timeline.hours().getFirst()).isEqualTo("Tue 28 Oct 2025 14:00");
    }

    @Test
    void shouldRoundWindToWholeKnots() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(forecastAt(START, 12.6, 16.4, "N"))), START, 1);

        WindTimeline.SpotWind spot = timeline.spots().getFirst();
        assertThat(spot.wind()).containsExactly(13);
        assertThat(spot.gusts()).containsExactly(16);
    }

    @Test
    void shouldIgnoreForecastsOutsideTheGrid() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(
                        forecastAt(START.minusHours(3), 30, 40, "N"),
                        forecastAt(START.plusHours(1), 12, 16, "S"),
                        forecastAt(START.plusHours(9), 30, 40, "N")
                )), START, 3);

        WindTimeline.SpotWind spot = timeline.spots().getFirst();
        // The out-of-grid neighbours contribute nothing; the 12 is held forward
        assertThat(spot.wind()).containsExactly(null, 12, 12).inOrder();
    }

    @Test
    void shouldSkipSpotsWithNothingOnTheGrid() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(
                        1, List.of(forecastAt(START, 12, 16, "N")),
                        2, List.of(forecastAt(START.plusDays(9), 12, 16, "N"))
                ), START, 3);

        assertThat(timeline.spots()).hasSize(1);
        assertThat(timeline.spots().getFirst().wgId()).isEqualTo(1);
    }

    @Test
    void shouldSkipSpotsWithoutHourlyForecasts() {
        WindTimeline timeline = mapper.toWindTimeline(Map.of(1, List.of()), START, 3);

        assertThat(timeline.spots()).isEmpty();
        assertThat(timeline.hours()).hasSize(3);
    }

    @Test
    void shouldLeaveDirectionEmptyForUnknownCardinal() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(forecastAt(START, 12, 16, "NNW"))), START, 1);

        assertThat(timeline.spots().getFirst().direction()).containsExactly((Integer) null);
    }

    @Test
    void shouldIgnoreForecastsWithUnparseableDates() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(
                        new Forecast("Tue 28. 14h", 12, 16, "N", 15, 0, 0, 1013),
                        forecastAt(START, 20, 26, "S")
                )), START, 2);

        assertThat(timeline.spots().getFirst().wind()).containsExactly(20, 20).inOrder();
    }

    @Test
    void shouldReturnEmptyTimelineWithoutForecasts() {
        WindTimeline timeline = mapper.toWindTimeline(Map.of(), START, 24);

        assertThat(timeline.hours()).isEmpty();
        assertThat(timeline.spots()).isEmpty();
    }

    @Test
    void shouldReturnEmptyTimelineForNonPositiveHourCount() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(forecastAt(START, 12, 16, "N"))), START, 0);

        assertThat(timeline.hours()).isEmpty();
        assertThat(timeline.spots()).isEmpty();
    }
}
