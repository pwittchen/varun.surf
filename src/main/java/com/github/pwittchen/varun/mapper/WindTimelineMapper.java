package com.github.pwittchen.varun.mapper;

import com.github.pwittchen.varun.model.forecast.Forecast;
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
 * Turns per-spot hourly forecasts into one shared hourly grid the map can step
 * through.
 *
 * Spots are not fetched at the same instant and a failed fetch leaves an older
 * series behind, so their hourly entries don't line up by index. Aligning them
 * on a grid of wall-clock hours here means the frontend can treat one slider
 * step as one index into every spot at once, instead of matching timestamps for
 * every spot on every repaint.
 */
@Component
public class WindTimelineMapper {

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
     * Lay hourly forecasts out on a shared grid.
     *
     * @param hourlyBySpotId hourly forecasts per Windguru spot id
     * @param start          first hour of the grid
     * @param hours          number of hours the grid spans
     * @return the timeline, with spots that have nothing on the grid left out
     */
    public WindTimeline toWindTimeline(Map<Integer, List<Forecast>> hourlyBySpotId, LocalDateTime start, int hours) {
        if (hourlyBySpotId == null || hourlyBySpotId.isEmpty() || hours < 1) {
            return new WindTimeline(List.of(), List.of());
        }

        final LocalDateTime gridStart = start.truncatedTo(ChronoUnit.HOURS);
        final List<String> grid = new ArrayList<>(hours);
        for (int hour = 0; hour < hours; hour++) {
            grid.add(gridStart.plusHours(hour).format(TIMESTAMP_FORMATTER));
        }

        final List<WindTimeline.SpotWind> spots = new ArrayList<>(hourlyBySpotId.size());
        hourlyBySpotId.forEach((spotId, forecasts) -> {
            WindTimeline.SpotWind spotWind = toSpotWind(spotId, forecasts, gridStart, hours);
            if (spotWind != null) {
                spots.add(spotWind);
            }
        });

        return new WindTimeline(grid, spots);
    }

    /**
     * Project one spot's forecasts onto the grid, or null when none of them land
     * on it - a spot whose forecast is entirely in the past carries no wind the
     * map could draw.
     */
    private WindTimeline.SpotWind toSpotWind(int spotId, List<Forecast> forecasts, LocalDateTime gridStart, int hours) {
        if (forecasts == null || forecasts.isEmpty()) {
            return null;
        }

        final Integer[] wind = new Integer[hours];
        final Integer[] gusts = new Integer[hours];
        final Integer[] direction = new Integer[hours];
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

            int slot = (int) index;
            wind[slot] = (int) Math.round(forecast.wind());
            gusts[slot] = (int) Math.round(forecast.gusts());
            int directionIndex = WindTimeline.DIRECTIONS.indexOf(forecast.direction());
            direction[slot] = directionIndex < 0 ? null : directionIndex;
            any = true;
        }

        if (!any) {
            return null;
        }

        holdSamplesForward(wind, gusts, direction);

        return new WindTimeline.SpotWind(
                spotId,
                Arrays.asList(wind),
                Arrays.asList(gusts),
                Arrays.asList(direction)
        );
    }

    /**
     * Carry each sample forward over the hours the coarser part of the forecast
     * skips, so the map keeps drawing wind past the three-hourly boundary instead
     * of blinking out for two hours in every three. Gaps wider than the stride are
     * left empty - there is no forecast there to speak for.
     *
     * The three series are filled together from the wind series, which is what
     * decides whether an hour has a sample at all, so a filled hour can never end
     * up with one hour's speed and another's direction.
     */
    private void holdSamplesForward(Integer[] wind, Integer[] gusts, Integer[] direction) {
        int held = 0;
        for (int hour = 1; hour < wind.length; hour++) {
            if (wind[hour] != null) {
                held = 0;
                continue;
            }
            if (wind[hour - 1] == null || held >= MAX_FILL_HOURS) {
                continue;
            }
            wind[hour] = wind[hour - 1];
            gusts[hour] = gusts[hour - 1];
            direction[hour] = direction[hour - 1];
            held++;
        }
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
