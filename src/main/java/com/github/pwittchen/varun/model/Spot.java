package com.github.pwittchen.varun.model;

import java.util.List;

public record Spot(
        String name,
        String country,
        String windguruUrl,
        String windFinderUrl,
        String icmUrl,
        String webcamUrl,
        String locationUrl,
        String lastUpdated,
        WeatherLive currentConditions,
        List<WeatherForecast> forecast
) {
}
