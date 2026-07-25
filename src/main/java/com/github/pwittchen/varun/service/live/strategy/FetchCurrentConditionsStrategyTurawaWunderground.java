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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Primary strategy for fetching current conditions for Turawa Lake
 * from the ISZCZE187 personal weather station in Szczedrzyk available on
 * <a href="https://www.wunderground.com/dashboard/pws/ISZCZE187">Weather Underground</a>.
 * When this station is unavailable or returns stale data,
 * {@link FetchCurrentConditionsStrategyTurawa} (airmax.pl) is used as a fallback.
 */
@Component
public class FetchCurrentConditionsStrategyTurawaWunderground extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int TURAWA_WG_ID = 726;
    private static final String STATION_ID = "ISZCZE187";
    private static final String OBSERVATIONS_URL =
            "https://api.weather.com/v2/pws/observations/current?stationId=%s&format=json&units=m&apiKey=%s";

    private static final double KMH_TO_KNOTS = 0.539957;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    public FetchCurrentConditionsStrategyTurawaWunderground(
            OkHttpClient httpClient,
            Gson gson,
            @Value("${app.wunderground.api-key:}") String apiKey
    ) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.apiKey = apiKey;
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == TURAWA_WG_ID;
    }

    @Override
    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        var url = getUrl(wgId);
        return fetchCurrentConditions(url);
    }

    @Override
    protected String getUrl(int wgId) {
        return OBSERVATIONS_URL.formatted(STATION_ID, apiKey);
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
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

                return createCurrentConditions(responseBody.string());
            }
        });
    }

    private CurrentConditions createCurrentConditions(String body) {
        JsonObject json = gson.fromJson(body, JsonObject.class);

        if (json == null || !json.has("observations")) {
            throw new RuntimeException("No current conditions data available");
        }

        JsonArray observations = json.getAsJsonArray("observations");
        if (observations == null || observations.isEmpty()) {
            throw new RuntimeException("No current conditions data available");
        }

        JsonObject observation = observations.get(0).getAsJsonObject();
        JsonObject metric = observation.getAsJsonObject("metric");
        if (metric == null) {
            throw new RuntimeException("No metric data available in current conditions");
        }

        String timestamp = extractString(observation, "obsTimeLocal", "timestamp");
        int windDirectionDegrees = (int) Math.round(extractDouble(observation, "winddir", "wind direction"));
        String windDirection = windDirectionDegreesToCardinal(windDirectionDegrees);
        int wind = (int) Math.round(extractDouble(metric, "windSpeed", "wind speed") * KMH_TO_KNOTS);
        int gusts = (int) Math.round(extractDouble(metric, "windGust", "wind gusts") * KMH_TO_KNOTS);
        int temperature = (int) Math.round(extractDouble(metric, "temp", "temperature"));

        return new CurrentConditions(timestamp, wind, gusts, windDirection, temperature);
    }

    private String extractString(JsonObject json, String field, String fieldName) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            throw new RuntimeException("Could not extract " + fieldName + " from response");
        }
        return json.get(field).getAsString();
    }

    private double extractDouble(JsonObject json, String field, String fieldName) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            throw new RuntimeException("Could not extract " + fieldName + " from response");
        }
        return json.get(field).getAsDouble();
    }
}
