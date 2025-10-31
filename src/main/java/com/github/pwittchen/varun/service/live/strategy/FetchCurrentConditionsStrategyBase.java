package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import okhttp3.OkHttpClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

public abstract class FetchCurrentConditionsStrategyBase {
    private static final List<String> WIND_DIRECTIONS = Arrays.asList("N", "NE", "E", "SE", "S", "SW", "W", "NW");

    protected final OkHttpClient httpClient = new OkHttpClient();

    protected abstract Mono<CurrentConditions> fetchCurrentConditions(String url);

    protected abstract String getUrl(int wgId);

    protected String normalizeDirection(String rawDirection) {
        if (WIND_DIRECTIONS.contains(rawDirection)) {
            return rawDirection;
        }

        return switch (rawDirection.toUpperCase()) {
            case "NNE", "ENE" -> "NE";
            case "ESE", "SSE" -> "SE";
            case "SSW", "WSW" -> "SW";
            case "WNW", "NNW" -> "NW";
            default -> findClosestDirection(rawDirection);
        };
    }

    protected String findClosestDirection(String rawDirection) {
        for (String direction : WIND_DIRECTIONS) {
            if (rawDirection.toUpperCase().startsWith(direction)) {
                return direction;
            }
        }
        return "N";
    }
}
