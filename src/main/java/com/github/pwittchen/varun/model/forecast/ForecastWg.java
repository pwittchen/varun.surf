package com.github.pwittchen.varun.model.forecast;

public record ForecastWg(
        String label,
        int windSpeed,
        int gust,
        int windDirectionDegrees,
        int temperature,
        int apcpMm1h,
        int cloudCoverPercent,
        int pressureHpa,
        Double waveHeight,
        Double wavePeriod,
        Integer waveDirectionDeg
) {
    public ForecastWg(String label, int windSpeed, int gust, int windDirectionDegrees,
                      int temperature, int apcpMm1h, int cloudCoverPercent, int pressureHpa) {
        this(label, windSpeed, gust, windDirectionDegrees, temperature, apcpMm1h,
             cloudCoverPercent, pressureHpa, null, null, null);
    }
}
