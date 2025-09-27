package com.github.pwittchen.varun.model;

public record Forecast(
        String date,
        double wind,
        double gusts,
        String direction,
        double temp,
        double precipitation
) {
}
