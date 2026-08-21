package com.github.pwittchen.varun.mapper;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.HourlyForecast;
import com.github.pwittchen.varun.model.forecast.WindTimeline;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lays hourly forecasts out on a grid of whole wall-clock hours starting now.
 *
 * Spots are not fetched at the same instant and a failed fetch leaves an older
 * series behind, so raw hourly entries neither line up with each other nor start
 * where the reader is. Aligning them here gives every consumer the same
 * guarantees: the first row is the current hour, one row per hour, and no holes
 * where the forecast steps to three-hourly.
 *
 * Two shapes come off the same alignment:
 * <ul>
 *   <li>{@link WindTimeline} - wind only, every spot at once, for the map, which
 *       draws all spots for a single moment and would otherwise pull megabytes</li>
 *   <li>{@link HourlyForecast} - every forecast field, one spot, for readers that
 *       want the whole picture (the AI analysis, API consumers)</li>
 * </ul>
 */
@Component
public class HourlyForecastMapper {

    // Same shape Forecast.date carries, so the frontend formats grid hours and
    // forecast dates with the one parser it already has.
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", Locale.ENGLISH);

    // Windguru drops to three-hourly steps after roughly three days, which leaves
    // two of every three hours empty from there on. A forecast for 15:00 is read
    // as covering the hours up to 18:00, so each sample is held forward across
    // that stride - long enough to close the cadence gap, short enough that a
    // genuinely missing tail stays visibly missing rather than being invented.
    private static final int MAX_FILL_HOURS = 2;

    /**
     * Wind for every spot, on one shared grid.
     *
     * @param hourlyBySpotId hourly forecasts per Windguru spot id
     * @param start          first hour of the grid
     * @param hours          number of hours the grid spans
     * @return the timeline, with spots that have nothing on the grid left out
     */
    public WindTimeline toWindTimeline(Map<Integer, List<Forecast>> hourlyBySpotId, LocalDateTime start, int hours) {
        if (hourlyBySpotId == null || hourlyBySpotId.isEmpty() || hours < 1) {
            return WindTimeline.EMPTY;
        }

        final LocalDateTime gridStart = start.truncatedTo(ChronoUnit.HOURS);
        final List<WindTimeline.SpotWind> spots = new ArrayList<>(hourlyBySpotId.size());

        hourlyBySpotId.forEach((spotId, forecasts) -> {
            Forecast[] slots = alignToGrid(forecasts, gridStart, hours);
            if (slots != null) {
                spots.add(toSpotWind(spotId, slots));
            }
        });

        return new WindTimeline(buildGrid(gridStart, hours), spots);
    }

    /**
     * Every forecast field of one spot, on the same grid.
     *
     * Hours the forecast says nothing about are left out rather than filled with
     * zeros, so a reader never mistakes a hole for a calm, rainless hour.
     *
     * @param wgId      Windguru id of the spot
     * @param forecasts the spot's hourly forecasts
     * @param start     first hour of the grid
     * @param hours     number of hours the grid spans
     * @return the spot's aligned forecast, empty when nothing lands on the grid
     */
    public HourlyForecast toHourlyForecast(int wgId, List<Forecast> forecasts, LocalDateTime start, int hours) {
        if (hours < 1) {
            return new HourlyForecast(wgId, List.of());
        }

        final LocalDateTime gridStart = start.truncatedTo(ChronoUnit.HOURS);
        final Forecast[] slots = alignToGrid(forecasts, gridStart, hours);
        if (slots == null) {
            return new HourlyForecast(wgId, List.of());
        }

        final List<String> grid = buildGrid(gridStart, hours);
        final List<Forecast> aligned = new ArrayList<>(hours);
        for (int hour = 0; hour < hours; hour++) {
            if (slots[hour] != null) {
                // A held-forward sample still carries the hour it was made for,
                // so the timestamp is restamped to the grid hour it stands in.
                aligned.add(withDate(slots[hour], grid.get(hour)));
            }
        }

        return new HourlyForecast(wgId, aligned);
    }

    private List<String> buildGrid(LocalDateTime gridStart, int hours) {
        final List<String> grid = new ArrayList<>(hours);
        for (int hour = 0; hour < hours; hour++) {
            grid.add(gridStart.plusHours(hour).format(TIMESTAMP_FORMATTER));
        }
        return grid;
    }

    /**
     * Drop one spot's forecasts into the hour they belong to and close the
     * three-hourly gaps, or return null when none of them land on the grid - a
     * forecast entirely in the past has nothing to say about the hours ahead.
     */
    private Forecast[] alignToGrid(List<Forecast> forecasts, LocalDateTime gridStart, int hours) {
        if (forecasts == null || forecasts.isEmpty()) {
            return null;
        }

        final Forecast[] slots = new Forecast[hours];
        boolean any = false;

        for (Forecast forecast : forecasts) {
            LocalDateTime time = parseTimestamp(forecast.date());
            if (time == null) {
                continue;
            }

            long index = ChronoUnit.HOURS.between(gridStart, time.truncatedTo(ChronoUnit.HOURS));
            if (index < 0 || index >= hours) {
                continue;
            }

            slots[(int) index] = forecast;
            any = true;
        }

        if (!any) {
            return null;
        }

        holdSamplesForward(slots);
        return slots;
    }

    /**
     * Carry each sample forward over the hours the coarser part of the forecast
     * skips, so readers keep seeing weather past the three-hourly boundary instead
     * of it blinking out for two hours in every three. Gaps wider than the stride
     * are left empty - there is no forecast there to speak for.
     */
    private void holdSamplesForward(Forecast[] slots) {
        int held = 0;
        for (int hour = 1; hour < slots.length; hour++) {
            if (slots[hour] != null) {
                held = 0;
                continue;
            }
            if (slots[hour - 1] == null || held >= MAX_FILL_HOURS) {
                continue;
            }
            slots[hour] = slots[hour - 1];
            held++;
        }
    }

    private WindTimeline.SpotWind toSpotWind(int spotId, Forecast[] slots) {
        final Integer[] wind = new Integer[slots.length];
        final Integer[] gusts = new Integer[slots.length];
        final Integer[] direction = new Integer[slots.length];

        for (int hour = 0; hour < slots.length; hour++) {
            Forecast forecast = slots[hour];
            if (forecast == null) {
                continue;
            }
            wind[hour] = (int) Math.round(forecast.wind());
            gusts[hour] = (int) Math.round(forecast.gusts());
            int directionIndex = WindTimeline.DIRECTIONS.indexOf(forecast.direction());
            direction[hour] = directionIndex < 0 ? null : directionIndex;
        }

        return new WindTimeline.SpotWind(
                spotId,
                Arrays.asList(wind),
                Arrays.asList(gusts),
                Arrays.asList(direction)
        );
    }

    private Forecast withDate(Forecast forecast, String date) {
        return new Forecast(
                date,
                forecast.wind(),
                forecast.gusts(),
                forecast.direction(),
                forecast.temp(),
                forecast.precipitation(),
                forecast.cloudCoverPercent(),
                forecast.pressureHpa(),
                forecast.wave(),
                forecast.wavePeriod(),
                forecast.waveDirection()
        );
    }

    private LocalDateTime parseTimestamp(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(date, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException e) {
            // Windguru labels that didn't match the hourly pattern are passed through
            // unparsed by WeatherForecastMapper; they carry no hour to place them at.
            return null;
        }
    }
}
