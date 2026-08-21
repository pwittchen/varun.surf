package com.github.pwittchen.varun.model.forecast;

import java.util.List;

/**
 * One spot's full hourly forecast, aligned to a grid of whole hours starting at
 * the current hour.
 *
 * The all-spots {@link WindTimeline} carries wind alone, because the map draws
 * every spot at once and anything more would be megabytes. A reader looking at a
 * single spot can afford the whole picture - temperature, rain, cloud, pressure
 * and waves alongside the wind - which is what this carries.
 *
 * Hours the forecast says nothing about are absent rather than zeroed, so a hole
 * is never mistaken for a calm, rainless hour.
 *
 * @param wgId  Windguru id of the spot
 * @param hours the aligned forecasts, each stamped with the grid hour it stands in
 */
public record HourlyForecast(
        int wgId,
        List<Forecast> hours
) {
    public HourlyForecast {
        hours = hours == null ? List.of() : List.copyOf(hours);
    }

    public boolean isEmpty() {
        return hours.isEmpty();
    }
}
