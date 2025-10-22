package com.github.pwittchen.varun.model;

import java.util.List;

// all models are documented here: https://micro.windguru.cz/help.php

public record ForecastData(
        List<Forecast> daily,
        List<Forecast> hourlyGfs,       // gfs
        List<Forecast> hourlyIfs,       // ifs
        List<Forecast> hourlyIcon,      // icon
        List<Forecast> hourlyGdps,      // gdps
        List<Forecast> hourlyHuWrf,     // huwrf
        List<Forecast> hourlyWrfCzs,    // wrfczs
        List<Forecast> hourlyAlacz      // alacz
) {
    public ForecastData {
        daily = daily == null ? List.of() : List.copyOf(daily);
        hourlyGfs = hourlyGfs == null ? List.of() : List.copyOf(hourlyGfs);
        hourlyIfs = hourlyIfs == null ? List.of() : List.copyOf(hourlyIfs);
        hourlyIcon = hourlyIcon == null ? List.of() : List.copyOf(hourlyIcon);
        hourlyGdps = hourlyGdps == null ? List.of() : List.copyOf(hourlyGdps);
        hourlyHuWrf = hourlyHuWrf == null ? List.of() : List.copyOf(hourlyHuWrf);
        hourlyWrfCzs = hourlyWrfCzs == null ? List.of() : List.copyOf(hourlyWrfCzs);
        hourlyAlacz = hourlyAlacz == null ? List.of() : List.copyOf(hourlyAlacz);
    }

    public ForecastData(List<Forecast> daily, List<Forecast> hourlyGfs) {
        this(daily, hourlyGfs, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
