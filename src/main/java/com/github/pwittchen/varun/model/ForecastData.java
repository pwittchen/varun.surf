package com.github.pwittchen.varun.model;

import java.util.List;

public record ForecastData(
        List<Forecast> daily,
        List<Forecast> hourly
) {
    public ForecastData {
        daily = daily == null ? List.of() : List.copyOf(daily);
        hourly = hourly == null ? List.of() : List.copyOf(hourly);
    }
}
