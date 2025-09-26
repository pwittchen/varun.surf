package com.github.pwittchen.varun.model.windguru;

public record WeatherForecastWindguru(
        String label,
        int windSpeed,
        int gust,
        String windDirectionCompass,
        int windDirectionDegrees,
        int temperature,
        int airPressureHpa,
        int cloudHighPct,
        int cloudMidPct,
        int cloudLowPct,
        int apcpMm3h,
        int apcpMm1h,
        int rhPct
) {
}
