package com.github.pwittchen.varun.model.forecast;

public record Forecast(
        String date,
        double wind,
        double gusts,
        String direction,
        double temp,
        double precipitation,
        double cloudCoverPercent,
        double pressureHpa,
        Double wave,
        Double wavePeriod,
        String waveDirection
) {
    public Forecast(String date, double wind, double gusts, String direction,
                    double temp, double precipitation, double cloudCoverPercent, double pressureHpa) {
        this(date, wind, gusts, direction, temp, precipitation, cloudCoverPercent, pressureHpa,
             null, null, null);
    }
}
