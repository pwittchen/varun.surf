package com.github.pwittchen.varun.model;

import java.util.List;

public record Spot(
        int id,
        String name,
        long updated,
        List<WeatherForecast> forecast
) {
}
