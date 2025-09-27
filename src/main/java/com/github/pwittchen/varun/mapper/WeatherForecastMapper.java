package com.github.pwittchen.varun.mapper;

import com.github.pwittchen.varun.model.WeatherForecast;
import com.github.pwittchen.varun.model.windguru.WeatherForecastWindguru;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    public List<WeatherForecast> toWeatherForecasts(List<WeatherForecastWindguru> forecasts) {
        final Map<String, WeatherForecastWindguru> windguruForecastMap = getWgForecastMapByDay(forecasts);
        return IntStream.range(0, DAYS.size())
                .mapToObj(i -> new WeatherForecast(
                        DAYS.get(i),
                        calculateWind(windguruForecastMap, i),
                        calculateGusts(windguruForecastMap, i),
                        calculateWindDirection(windguruForecastMap, i),
                        calculateTemperature(windguruForecastMap, i),
                        calculatePrecipitation(windguruForecastMap, i),
                        calculateWave()
                ))
                .collect(Collectors.toList());
    }

    private static int calculateWave() {
        // right now in the micro forecast wave is not available, so we are having this placeholder here
        return 0;
    }

    private static double calculatePrecipitation(Map<String, WeatherForecastWindguru> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().apcpMm1h())
                .average()
                .orElse(0);
    }

    private static double calculateTemperature(Map<String, WeatherForecastWindguru> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().temperature())
                .average()
                .orElse(0);
    }

    private static String calculateWindDirection(Map<String, WeatherForecastWindguru> map, int dayIndex) {
        double avgWindDirectionDegrees = map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().windDirectionDegrees())
                .average()
                .orElse(0);

        return getWindDirection(avgWindDirectionDegrees);
    }

    private static String getWindDirection(double degrees) {
        double normalized = (degrees % 360 + 360) % 360;
        int index = (int) Math.round(normalized / 45) % DIRECTIONS.size();
        return DIRECTIONS.get(index);
    }

    private static double calculateGusts(Map<String, WeatherForecastWindguru> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().gust())
                .average()
                .orElse(0);
    }

    private static double calculateWind(Map<String, WeatherForecastWindguru> map, int dayIndex) {
        return map
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(DAYS.get(dayIndex)))
                .mapToInt(entry -> entry.getValue().windSpeed())
                .average()
                .orElse(0);
    }

    private static Map<String, WeatherForecastWindguru> getWgForecastMapByDay(List<WeatherForecastWindguru> forecasts) {
        final Map<String, WeatherForecastWindguru> windguruForecastMap = new HashMap<>();
        int dayIndex = 0;
        String labelPrefix = "";

        for (WeatherForecastWindguru f : forecasts) {
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
