package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.ForecastData;
import com.github.pwittchen.varun.model.forecast.ForecastModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ForecastAverageCalculator {

    public static final String AVERAGE_MODEL_KEY = "average";
    public static final String AVERAGE_DISPLAY_NAME = "AVERAGE (all models)";

    private static final List<String> DIRECTIONS = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");
    private static final int MIN_MODELS_FOR_AVERAGE = 2;

    private ForecastAverageCalculator() {
    }

    public static List<Forecast> computeAverage(ForecastData forecastData) {
        Map<ForecastModel, List<Forecast>> hourlyByModel = forecastData.hourly();

        List<Map.Entry<ForecastModel, List<Forecast>>> nonEmpty = hourlyByModel.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .toList();

        if (nonEmpty.size() < MIN_MODELS_FOR_AVERAGE) {
            return List.of();
        }

        // Group forecasts by date string across all models
        // LinkedHashMap to preserve insertion order (time order from first model)
        Map<String, List<Forecast>> forecastsByDate = new LinkedHashMap<>();
        for (var entry : nonEmpty) {
            for (Forecast f : entry.getValue()) {
                forecastsByDate.computeIfAbsent(f.date(), _ -> new ArrayList<>()).add(f);
            }
        }

        List<Forecast> averaged = new ArrayList<>();
        for (var entry : forecastsByDate.entrySet()) {
            List<Forecast> forecasts = entry.getValue();
            if (forecasts.size() < MIN_MODELS_FOR_AVERAGE) {
                continue;
            }

            double avgWind = round1(forecasts.stream().mapToDouble(Forecast::wind).average().orElse(0));
            double avgGusts = round1(forecasts.stream().mapToDouble(Forecast::gusts).average().orElse(0));
            double avgTemp = round1(forecasts.stream().mapToDouble(Forecast::temp).average().orElse(0));
            double avgPrecip = round1(forecasts.stream().mapToDouble(Forecast::precipitation).average().orElse(0));
            String avgDirection = circularMeanDirection(forecasts.stream().map(Forecast::direction).toList());
            double avgCloudCover = round1(forecasts.stream().mapToDouble(Forecast::cloudCoverPercent).average().orElse(0));
            double avgPressure = round1(forecasts.stream().mapToDouble(Forecast::pressureHpa).average().orElse(0));

            averaged.add(new Forecast(entry.getKey(), avgWind, avgGusts, avgDirection, avgTemp, avgPrecip, avgCloudCover, avgPressure));
        }

        return averaged;
    }

    static String circularMeanDirection(List<String> cardinalDirections) {
        double sinSum = 0;
        double cosSum = 0;
        int count = 0;

        for (String cardinal : cardinalDirections) {
            int deg = cardinalToDegrees(cardinal);
            if (deg < 0) {
                continue;
            }
            double rad = Math.toRadians(deg);
            sinSum += Math.sin(rad);
            cosSum += Math.cos(rad);
            count++;
        }

        if (count == 0) {
            return "N";
        }

        double meanRad = Math.atan2(sinSum / count, cosSum / count);
        double meanDeg = Math.toDegrees(meanRad);
        double normalized = ((meanDeg % 360) + 360) % 360;
        int index = (int) Math.round(normalized / 45) % DIRECTIONS.size();
        return DIRECTIONS.get(index);
    }

    private static int cardinalToDegrees(String cardinal) {
        if (cardinal == null || cardinal.isEmpty()) {
            return -1;
        }
        return switch (cardinal.toUpperCase()) {
            case "N" -> 0;
            case "NE" -> 45;
            case "E" -> 90;
            case "SE" -> 135;
            case "S" -> 180;
            case "SW" -> 225;
            case "W" -> 270;
            case "NW" -> 315;
            default -> -1;
        };
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
