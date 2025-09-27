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
        final Map<String, ForecastWg> windguruForecastMap = getWgForecastMapByDay(forecasts);
        return IntStream.range(0, DAYS.size())
                .mapToObj(i -> new Forecast(
                        DAYS.get(i),
                        calculateWind(windguruForecastMap, i),
                        calculateGusts(windguruForecastMap, i),
                        calculateWindDirection(windguruForecastMap, i),
                        calculateTemperature(windguruForecastMap, i),
                        calculatePrecipitation(windguruForecastMap, i)
                ))
                .collect(Collectors.toList());
    }

    private double calculatePrecipitation(Map<String, ForecastWg> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().apcpMm1h())
                .average()
                .orElse(0);
    }

    private double calculateTemperature(Map<String, ForecastWg> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().temperature())
                .average()
                .orElse(0);
    }

    private String calculateWindDirection(Map<String, ForecastWg> map, int dayIndex) {
        double avgWindDirectionDegrees = map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().windDirectionDegrees())
                .average()
                .orElse(0);

        return getWindDirection(avgWindDirectionDegrees);
    }

    private String getWindDirection(double degrees) {
        double normalized = (degrees % 360 + 360) % 360;
        int index = (int) Math.round(normalized / 45) % DIRECTIONS.size();
        return DIRECTIONS.get(index);
    }

    private double calculateGusts(Map<String, ForecastWg> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().gust())
                .average()
                .orElse(0);
    }

    private double calculateWind(Map<String, ForecastWg> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().windSpeed())
                .average()
                .orElse(0);
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
}
