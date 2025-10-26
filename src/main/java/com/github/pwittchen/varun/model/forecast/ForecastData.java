package com.github.pwittchen.varun.model.forecast;

import java.util.List;

// all models are documented here: https://micro.windguru.cz/help.php

public record ForecastData(
        List<Forecast> daily,
        List<Forecast> hourlyGfs,       // gfs
        List<Forecast> hourlyIfs        // ifs
) {
    public ForecastData {
        daily = daily == null ? List.of() : List.copyOf(daily);
        hourlyGfs = hourlyGfs == null ? List.of() : List.copyOf(hourlyGfs);
        hourlyIfs = hourlyIfs == null ? List.of() : List.copyOf(hourlyIfs);
    }
}
