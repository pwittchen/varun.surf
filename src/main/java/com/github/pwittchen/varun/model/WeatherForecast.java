package com.github.pwittchen.varun.model;

public record WeatherForecast(
        String date,
        double wind,
        double gusts,
        String direction,
        double temp,
        double precipitation,
        double wave
) {
}
