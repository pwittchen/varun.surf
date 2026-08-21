package com.github.pwittchen.varun.model.forecast;

import java.util.List;

/**
 * Hourly wind for every spot at once, laid out on one shared time grid.
 *
 * The map paints all spots for a single moment, so it needs the same hour from
 * every spot rather than every field of one spot. Serving the full hourly
 * forecasts for the whole database would be megabytes; this carries only what a
 * wind arrow or an interpolated field is drawn from, aligned to a common grid so
 * a step on the timeline is one index into every spot's arrays.
 *
 * Values are parallel to {@code hours} and hold null where a spot has no
 * forecast for that hour. Directions are indices into {@link #DIRECTIONS} rather
 * than names, which keeps the payload small.
 *
 * @param hours forecast timestamps, formatted the same way Forecast.date is
 * @param spots per-spot wind series, one entry per spot that has hourly data
 */
public record WindTimeline(
        List<String> hours,
        List<SpotWind> spots
) {
    /**
     * Cardinal directions in the order the {@code direction} indices refer to.
     */
    public static final List<String> DIRECTIONS = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");

    public WindTimeline {
        hours = hours == null ? List.of() : List.copyOf(hours);
        spots = spots == null ? List.of() : List.copyOf(spots);
    }

    /**
     * One spot's wind over the shared grid.
     *
     * @param wgId      Windguru id, which is how the frontend joins this to a spot
     * @param wind      wind speed in knots per hour of the grid
     * @param gusts     gust speed in knots per hour of the grid
     * @param direction index into {@link #DIRECTIONS} per hour of the grid
     */
    public record SpotWind(
            int wgId,
            List<Integer> wind,
            List<Integer> gusts,
            List<Integer> direction
    ) {
    }
}
