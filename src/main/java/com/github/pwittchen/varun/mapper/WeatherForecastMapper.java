package com.github.pwittchen.varun.mapper;

import com.github.pwittchen.varun.model.Forecast;
import com.github.pwittchen.varun.model.ForecastWg;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class WeatherForecastMapper {
    private static final List<String> DAYS = Arrays.asList("Today", "Tomorrow", "Day 3", "Day 4", "Day 5");
    private static final List<String> DIRECTIONS = Arrays.asList("N", "NE", "E", "SE", "S", "SW", "W", "NW");

    public List<Forecast> toWeatherForecasts(List<ForecastWg> forecasts) {
        final Map<String, ForecastWg> wgForecastsByDay = getWgForecastMapByDay(forecasts);
        return IntStream
                .range(0, DAYS.size())
                .mapToObj(dayIndex -> createForecast(dayIndex, wgForecastsByDay))
                .collect(Collectors.toList());
    }

    private Map<String, ForecastWg> getWgForecastMapByDay(List<ForecastWg> forecasts) {
        final Map<String, ForecastWg> windguruForecastMap = new HashMap<>();
        int dayIndex = 0;
        String labelPrefix = "";

        for (ForecastWg f : forecasts) {
            if (dayIndex == DAYS.size() - 1) {
                break;
            }
            if (!f.label().substring(0, 2).equals(labelPrefix) && !labelPrefix.isEmpty()) {
                dayIndex++;
            }
            labelPrefix = f.label().substring(0, 2);
            windguruForecastMap.put(DAYS.get(dayIndex), f);
        }
        return windguruForecastMap;
    }

    private Forecast createForecast(int dayIndex, Map<String, ForecastWg> wgForecastsByDay) {
        return new Forecast(
                DAYS.get(dayIndex),
                calculateAvgWind(wgForecastsByDay, dayIndex),
                calculateAvgGusts(wgForecastsByDay, dayIndex),
                calculateAvgWindDirection(wgForecastsByDay, dayIndex),
                calculateAvgTemperature(wgForecastsByDay, dayIndex),
                calculateAvgPrecipitation(wgForecastsByDay, dayIndex)
        );
    }

    private double calculateAvgWind(Map<String, ForecastWg> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().windSpeed())
                .average()
                .orElse(0);
    }

    private double calculateAvgGusts(Map<String, ForecastWg> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().gust())
                .average()
                .orElse(0);
    }

    private String calculateAvgWindDirection(Map<String, ForecastWg> map, int dayIndex) {
        return estimateWindDirection(map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().windDirectionDegrees())
                .average()
                .orElse(0));
    }

    private String estimateWindDirection(double degrees) {
        double normalized = (degrees % 360 + 360) % 360;
        int index = (int) Math.round(normalized / 45) % DIRECTIONS.size();
        return DIRECTIONS.get(index);
    }

    private double calculateAvgTemperature(Map<String, ForecastWg> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().temperature())
                .average()
                .orElse(0);
    }

    private double calculateAvgPrecipitation(Map<String, ForecastWg> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().apcpMm1h())
                .average()
                .orElse(0);
    }
}
