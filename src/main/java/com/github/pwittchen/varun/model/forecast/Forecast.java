package com.github.pwittchen.varun.model.forecast;

public record Forecast(
        String date,
        double wind,
        double gusts,
        String direction,
        double temp,
        double precipitation
) {
}
