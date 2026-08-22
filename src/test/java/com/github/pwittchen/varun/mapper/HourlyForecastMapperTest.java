package com.github.pwittchen.varun.mapper;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.HourlyForecast;
import com.github.pwittchen.varun.model.forecast.WindTimeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

class HourlyForecastMapperTest {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", Locale.ENGLISH);

    private static final LocalDateTime START = LocalDateTime.of(2025, 10, 28, 14, 0);

    private HourlyForecastMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new HourlyForecastMapper();
    }

    private Forecast forecastAt(LocalDateTime time, double wind, double gusts, String direction) {
        return new Forecast(time.format(FORMATTER), wind, gusts, direction, 15, 0, 0, 1013);
    }

    @Test
    void shouldBuildHourlyGridUpToTheRequestedLength() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(
                        forecastAt(START, 12, 16, "NW"),
                        forecastAt(START.plusHours(3), 20, 26, "SW")
                )), START, 4);

        assertThat(timeline.hours()).hasSize(4);
        assertThat(timeline.hours().getFirst()).isEqualTo("Tue 28 Oct 2025 14:00");
        assertThat(timeline.hours().getLast()).isEqualTo("Tue 28 Oct 2025 17:00");
    }

    @Test
    void shouldEndTheGridWhereTheForecastEnds() {
        // asking for more hours than the forecast reaches is how the map asks for
        // everything there is, and must not grow a tail of hours that draw nothing
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(1, List.of(forecastAt(START, 12, 16, "NW"))), START, 240);

        // the lone sample stands for its own hour and the two after it
        assertThat(timeline.hours()).hasSize(3);
        assertThat(timeline.hours().getLast()).isEqualTo("Tue 28 Oct 2025 16:00");
        assertThat(timeline.spots().getFirst().wind()).containsExactly(12, 12, 12).inOrder();
    }

    @Test
    void shouldEndTheGridWhereTheForecastStopsCoveringMostSpots() {
        // the very last hours of a run belong to whichever few spots reach furthest,
        // and a map drawn from those alone shows next to nothing
        Map<Integer, List<Forecast>> hourly = new java.util.LinkedHashMap<>();
        for (int spot = 1; spot <= 9; spot++) {
            hourly.put(spot, List.of(forecastAt(START, 12, 16, "N")));
        }
        hourly.put(10, List.of(forecastAt(START, 12, 16, "N"), forecastAt(START.plusHours(12), 20, 26, "S")));

        WindTimeline timeline = mapper.toWindTimeline(hourly, START, 240);

        // the lone long-running spot doesn't stretch the grid past the other nine
        assertThat(timeline.hours()).hasSize(3);
        assertThat(timeline.spots()).hasSize(10);
    }

    @Test
    void shouldStretchTheGridToTheLongestForecastOnIt() {
        WindTimeline timeline = mapper.toWindTimeline(
                Map.of(
                        1, List.of(forecastAt(START, 12, 16, "N")),
                        2, List.of(forecastAt(START, 12, 16, "N"), forecastAt(START.plusHours(6), 20, 26, "S"))
                ), START, 240);

        assertThat(timeline.hours()).hasSize(9);
        // the shorter spot keeps its length, padded with the hours it says nothing about
        WindTimeline.SpotWind shorter = timeline.spots().stream()
                .filter(spot -> spot.wgId() == 1)
                .findFirst()
                .orElseThrow();
        assertThat(shorter.wind()).containsExactly(12, 12, 12, null, null, null, null, null, null).inOrder();
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
    void shouldReturnEmptyTimelineWhenNoSpotHasAnythingOnTheGrid() {
        // no spot on the grid means no hours worth stepping through either
        WindTimeline timeline = mapper.toWindTimeline(Map.of(1, List.of()), START, 3);

        assertThat(timeline.spots()).isEmpty();
        assertThat(timeline.hours()).isEmpty();
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

    // ========================================================================
    // Full hourly forecast for a single spot
    // ========================================================================

    private Forecast fullForecastAt(LocalDateTime time) {
        return new Forecast(time.format(FORMATTER), 12, 16, "NW", 21, 0.4, 40, 1013, 0.8, 4.0, "SW");
    }

    @Test
    void shouldKeepEveryForecastFieldOnTheGrid() {
        HourlyForecast forecast = mapper.toHourlyForecast(
                1, List.of(fullForecastAt(START)), START, 1);

        assertThat(forecast.wgId()).isEqualTo(1);
        assertThat(forecast.hours()).hasSize(1);

        Forecast hour = forecast.hours().getFirst();
        assertThat(hour.date()).isEqualTo("Tue 28 Oct 2025 14:00");
        assertThat(hour.wind()).isEqualTo(12.0);
        assertThat(hour.gusts()).isEqualTo(16.0);
        assertThat(hour.direction()).isEqualTo("NW");
        assertThat(hour.temp()).isEqualTo(21.0);
        assertThat(hour.precipitation()).isEqualTo(0.4);
        assertThat(hour.cloudCoverPercent()).isEqualTo(40.0);
        assertThat(hour.pressureHpa()).isEqualTo(1013.0);
        assertThat(hour.wave()).isEqualTo(0.8);
        assertThat(hour.wavePeriod()).isEqualTo(4.0);
        assertThat(hour.waveDirection()).isEqualTo("SW");
    }

    @Test
    void shouldRestampHeldForwardHoursToTheirGridHour() {
        // a three-hourly sample stands in for the two hours after it, and each row
        // has to say which hour it stands in - not the hour the forecast was made for
        HourlyForecast forecast = mapper.toHourlyForecast(
                1, List.of(fullForecastAt(START)), START, 3);

        assertThat(forecast.hours()).hasSize(3);
        assertThat(forecast.hours().stream().map(Forecast::date))
                .containsExactly(
                        "Tue 28 Oct 2025 14:00",
                        "Tue 28 Oct 2025 15:00",
                        "Tue 28 Oct 2025 16:00"
                ).inOrder();
    }

    @Test
    void shouldLeaveOutHoursTheForecastSaysNothingAbout() {
        // rather than emit zeros, which would read as a calm, rainless hour
        HourlyForecast forecast = mapper.toHourlyForecast(
                1, List.of(fullForecastAt(START), fullForecastAt(START.plusHours(5))), START, 6);

        // hours 0-2 held forward from the first sample, 3-4 empty, 5 from the second
        assertThat(forecast.hours()).hasSize(4);
        assertThat(forecast.hours().stream().map(Forecast::date))
                .containsExactly(
                        "Tue 28 Oct 2025 14:00",
                        "Tue 28 Oct 2025 15:00",
                        "Tue 28 Oct 2025 16:00",
                        "Tue 28 Oct 2025 19:00"
                ).inOrder();
    }

    @Test
    void shouldReturnEmptyHourlyForecastWhenNothingLandsOnTheGrid() {
        HourlyForecast forecast = mapper.toHourlyForecast(
                1, List.of(fullForecastAt(START.plusDays(9))), START, 3);

        assertThat(forecast.wgId()).isEqualTo(1);
        assertThat(forecast.isEmpty()).isTrue();
    }

    @Test
    void shouldReturnEmptyHourlyForecastWithoutForecasts() {
        assertThat(mapper.toHourlyForecast(1, List.of(), START, 3).isEmpty()).isTrue();
        assertThat(mapper.toHourlyForecast(1, null, START, 3).isEmpty()).isTrue();
        assertThat(mapper.toHourlyForecast(1, List.of(fullForecastAt(START)), START, 0).isEmpty()).isTrue();
    }

    @Test
    void shouldKeepNullWaveFieldsForInlandSpots() {
        HourlyForecast forecast = mapper.toHourlyForecast(
                1, List.of(forecastAt(START, 12, 16, "N")), START, 1);

        Forecast hour = forecast.hours().getFirst();
        assertThat(hour.wave()).isNull();
        assertThat(hour.wavePeriod()).isNull();
        assertThat(hour.waveDirection()).isNull();
    }
}
