package com.github.pwittchen.varun.model.windguru;

import java.util.List;

public record SpotWindguru(
        int id,
        String name,
        long updated,
        List<WeatherForecastWindguru> forecast
) {
}
