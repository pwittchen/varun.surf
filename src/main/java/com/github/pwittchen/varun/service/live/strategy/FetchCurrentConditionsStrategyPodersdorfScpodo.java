package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditionsStrategyBase;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Fallback strategy for fetching current conditions from the Podersdorf spot in Austria
 * using the scpodo.at weather station data.
 * This strategy serves as a backup when the primary kiteriders.at station is unavailable or returns stale data.
 */
@Component
public class FetchCurrentConditionsStrategyPodersdorfScpodo extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int PODERSDORF_WG_ID = 859182;
    private static final String SCPODO_WIND_URL = "https://scpodo.at/wind.php";

    private final OkHttpClient httpClient;
    private final Gson gson;

    public FetchCurrentConditionsStrategyPodersdorfScpodo(OkHttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == PODERSDORF_WG_ID;
    }

    @Override
    public boolean isFallbackStation() {
        return true;
    }

    @Override
    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        var url = getUrl(wgId);
        return fetchCurrentConditions(url);
    }

    @Override
    protected Mono<CurrentConditions> fetchCurrentConditions(String url) {
        return Mono.fromCallable(() -> {
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to fetch current conditions: " + response);
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new RuntimeException("Failed to fetch current conditions: response body is null");
                }

                String body = responseBody.string();
                JsonArray jsonArray = gson.fromJson(body, JsonArray.class);

                if (jsonArray == null || jsonArray.isEmpty()) {
                    throw new RuntimeException("No current conditions data available");
                }

                // Get the most recent reading (first element in array)
                JsonObject latestData = jsonArray.get(0).getAsJsonObject();

                String timestamp = latestData.get("DateTime").getAsString();
                double windSpeed = Double.parseDouble(latestData.get("windSpeedCur").getAsString());
                double windGusts = Double.parseDouble(latestData.get("windSpeedGusts").getAsString());
                String windDirectionDe = latestData.get("windDirCurDe").getAsString();
                double temp = Double.parseDouble(latestData.get("tempOutCur").getAsString());

                String windDirection = normalizeGermanDirection(windDirectionDe);
                int wind = (int) Math.round(windSpeed);
                int gusts = (int) Math.round(windGusts);
                int temperature = (int) Math.round(temp);

                return new CurrentConditions(timestamp, wind, gusts, windDirection, temperature);
            }
        });
    }

    @Override
    protected String getUrl(int wgId) {
        return SCPODO_WIND_URL;
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Normalizes German wind direction abbreviations to English cardinal directions.
     * German abbreviations: N, NO, O, SO, S, SW, W, NW
     * English: N, NE, E, SE, S, SW, W, NW
     */
    private String normalizeGermanDirection(String germanDirection) {
        return switch (germanDirection.toUpperCase()) {
            case "N" -> "N";
            case "NO", "NNO" -> "NE";
            case "O", "ONO" -> "E";
            case "SO", "OSO", "SSO" -> "SE";
            case "S" -> "S";
            case "SW", "SSW", "WSW" -> "SW";
            case "W", "WNW" -> "W";
            case "NW", "NNW" -> "NW";
            default -> normalizeDirection(germanDirection);
        };
    }
}
