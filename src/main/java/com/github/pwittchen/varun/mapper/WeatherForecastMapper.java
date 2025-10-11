package com.github.pwittchen.varun.mapper;

import com.github.pwittchen.varun.model.Forecast;
import com.github.pwittchen.varun.model.ForecastWg;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class WeatherForecastMapper {
    private static final List<String> DAYS = Arrays.asList("Today", "Tomorrow", "Day 3", "Day 4", "Day 5");
    private static final List<String> DIRECTIONS = Arrays.asList("N", "NE", "E", "SE", "S", "SW", "W", "NW");
    private static final Pattern HOURLY_LABEL_PATTERN = Pattern.compile("(?i)(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s+(\\d{1,2})\\.\\s+(\\d{2})h");
    private static final DateTimeFormatter HOURLY_OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final ZoneId FORECAST_ZONE = ZoneId.systemDefault();

    public List<Forecast> toWeatherForecasts(List<ForecastWg> forecasts) {
        final Map<String, ForecastWg> wgForecastsByDay = getWgForecastMapByDay(forecasts);
        return IntStream
                .range(0, DAYS.size())
                .mapToObj(dayIndex -> createForecast(dayIndex, wgForecastsByDay))
                .collect(Collectors.toList());
    }

    public List<Forecast> toHourlyForecasts(List<ForecastWg> forecasts) {
        List<Forecast> result = new ArrayList<>(forecasts.size());
        YearMonth currentYearMonth = YearMonth.from(LocalDate.now(FORECAST_ZONE));
        LocalDate previousDate = null;
        int previousHour = -1;

        for (ForecastWg forecast : forecasts) {
            Matcher matcher = HOURLY_LABEL_PATTERN.matcher(forecast.label());
            if (!matcher.matches()) {
                result.add(new Forecast(
                        forecast.label(),
                        forecast.windSpeed(),
                        forecast.gust(),
                        estimateWindDirection(forecast.windDirectionDegrees()),
                        forecast.temperature(),
                        forecast.apcpMm1h()
                ));
                continue;
            }

            DayOfWeek dayOfWeek = parseDayOfWeek(matcher.group(1));
            int dayOfMonth = Integer.parseInt(matcher.group(2));
            int hour = Integer.parseInt(matcher.group(3));

            LocalDate forecastDate = resolveForecastDate(currentYearMonth, dayOfMonth, dayOfWeek, previousDate, hour, previousHour);
            currentYearMonth = YearMonth.from(forecastDate);

            LocalDateTime dateTime = LocalDateTime.of(forecastDate, LocalTime.of(hour, 0));
            result.add(new Forecast(
                    dateTime.format(HOURLY_OUTPUT_FORMATTER),
                    forecast.windSpeed(),
                    forecast.gust(),
                    estimateWindDirection(forecast.windDirectionDegrees()),
                    forecast.temperature(),
                    forecast.apcpMm1h()
            ));

            previousDate = forecastDate;
            previousHour = hour;
        }

        return result;
    }

    private LocalDate resolveForecastDate(
            YearMonth baseYearMonth,
            int dayOfMonth,
            DayOfWeek expectedDayOfWeek,
            LocalDate previousDate,
            int hour,
            int previousHour
    ) {
        YearMonth candidateYearMonth = baseYearMonth;
        LocalDate today = LocalDate.now(FORECAST_ZONE);

        while (true) {
            while (dayOfMonth > candidateYearMonth.lengthOfMonth()) {
                candidateYearMonth = candidateYearMonth.plusMonths(1);
            }

            LocalDate candidateDate = candidateYearMonth.atDay(dayOfMonth);

            if (!candidateDate.getDayOfWeek().equals(expectedDayOfWeek)) {
                candidateYearMonth = candidateYearMonth.plusMonths(1);
                continue;
            }

            if (previousDate == null) {
                if (candidateDate.isBefore(today)) {
                    candidateYearMonth = candidateYearMonth.plusMonths(1);
                    continue;
                }
            } else {
                if (candidateDate.isBefore(previousDate) ||
                        (candidateDate.isEqual(previousDate) && hour < previousHour)) {
                    candidateYearMonth = candidateYearMonth.plusMonths(1);
                    continue;
                }
            }

            return candidateDate;
        }
    }

    private DayOfWeek parseDayOfWeek(String abbreviation) {
        return switch (abbreviation.toLowerCase(Locale.ENGLISH)) {
            case "mon" -> DayOfWeek.MONDAY;
            case "tue" -> DayOfWeek.TUESDAY;
            case "wed" -> DayOfWeek.WEDNESDAY;
            case "thu" -> DayOfWeek.THURSDAY;
            case "fri" -> DayOfWeek.FRIDAY;
            case "sat" -> DayOfWeek.SATURDAY;
            case "sun" -> DayOfWeek.SUNDAY;
            default -> DayOfWeek.MONDAY;
        };
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
                .mapToInt(entry -> entry.getValue().apcpMm1h() * 10)
                .average()
                .orElse(0);
    }
}
