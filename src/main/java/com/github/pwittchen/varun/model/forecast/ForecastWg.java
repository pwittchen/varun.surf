package com.github.pwittchen.varun.model.forecast;

public record ForecastWg(
        String label,
        int windSpeed,
        int gust,
        int windDirectionDegrees,
        int temperature,
        int apcpMm1h
) {
}
