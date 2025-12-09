package com.github.pwittchen.varun.model.forecast;

import java.util.List;
import java.util.Map;

// all models are documented here: https://micro.windguru.cz/help.php

public record ForecastData(
        List<Forecast> daily,
        Map<ForecastModel, List<Forecast>> hourly
) {
    public ForecastData {
        daily = daily == null ? List.of() : List.copyOf(daily);
        hourly = hourly == null ? Map.of() : Map.copyOf(hourly);
    }

    public List<Forecast> hourly(ForecastModel model) {
        return hourly.getOrDefault(model, List.of());
    }
}
