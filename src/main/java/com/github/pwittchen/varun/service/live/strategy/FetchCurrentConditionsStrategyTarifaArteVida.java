package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditionsStrategyBase;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy for fetching current conditions from Spotfav Weatherflow stations.
 * Used for Tarifa, Arte Vida spot which has a Weatherflow Tempest station.
 *
 * The Spotfav page returns JavaScript with embedded JSON data containing hourly forecasts.
 * The JSON is HTML-encoded (using &quot; for quotes, etc.) and wrapped in {"hours": [...]}.
 * We extract the most recent data point from the hours array.
 */
@Component
public class FetchCurrentConditionsStrategyTarifaArteVida extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int TARIFA_ARTE_VIDA_WG_ID = 48775;
    private static final String SPOTFAV_URL = "https://www.spotfav.com/public/meteo/weatherflow-4eee927b185476763900001b/update/";

    // Regex pattern to extract hourly_weather_forecast JSON from JavaScript
    // Format: const hourly_weather_forecast = JSON.parse(("...").replaceAll(...))
    private static final Pattern FORECAST_JSON_PATTERN = Pattern.compile(
            "const\\s+hourly_weather_forecast\\s*=\\s*JSON\\.parse\\(\\(\"(.+?)\"\\)"
    );

    // Conversion factor from m/s to knots
    private static final double MS_TO_KNOTS = 1.94384;

    private final OkHttpClient httpClient;
    private final Gson gson;

    public FetchCurrentConditionsStrategyTarifaArteVida(OkHttpClient httpClient) {
        this.httpClient = httpClient;
        this.gson = new Gson();
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == TARIFA_ARTE_VIDA_WG_ID;
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
                return parseJavaScriptResponse(body);
            }
        });
    }

    private CurrentConditions parseJavaScriptResponse(String content) {
        // Extract the JSON from the JavaScript variable assignment
        Matcher matcher = FORECAST_JSON_PATTERN.matcher(content);
        if (!matcher.find()) {
            throw new RuntimeException("Could not find hourly_weather_forecast in response");
        }

        String jsonString = matcher.group(1);

        // Decode HTML entities (the JSON uses &quot; for quotes, &amp; for &, etc.)
        jsonString = decodeHtmlEntities(jsonString);

        // Parse the JSON - it's wrapped in {"hours": [...]}
        JsonObject rootObject = gson.fromJson(jsonString, JsonObject.class);
        if (rootObject == null) {
            throw new RuntimeException("Failed to parse forecast JSON");
        }

        JsonArray forecastArray = rootObject.getAsJsonArray("hours");
        if (forecastArray == null || forecastArray.isEmpty()) {
            throw new RuntimeException("Empty forecast data - no hours array found");
        }

        // Find the entry closest to current time
        JsonObject currentData = findCurrentEntry(forecastArray);

        // Extract wind data - values are nested under "sg" key
        double windSpeedMs = getNestedDouble(currentData, "windSpeed", "sg");
        double gustMs = getNestedDouble(currentData, "gust", "sg");
        int windDirectionDeg = (int) getNestedDouble(currentData, "windDirection", "sg");
        int temperature = (int) Math.round(getNestedDouble(currentData, "airTemperature", "sg"));

        // Convert m/s to knots
        int windSpeedKnots = (int) Math.round(windSpeedMs * MS_TO_KNOTS);
        int gustKnots = (int) Math.round(gustMs * MS_TO_KNOTS);

        String direction = windDirectionDegreesToCardinal(windDirectionDeg);
        String timestamp = formatTimestamp();

        return new CurrentConditions(timestamp, windSpeedKnots, gustKnots, direction, temperature);
    }

    private String decodeHtmlEntities(String input) {
        return input
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#039;", "'")
                .replace("&apos;", "'");
    }

    private JsonObject findCurrentEntry(JsonArray forecastArray) {
        LocalDateTime now = LocalDateTime.now();
        JsonObject bestMatch = null;
        long smallestDiff = Long.MAX_VALUE;

        for (JsonElement element : forecastArray) {
            JsonObject entry = element.getAsJsonObject();
            JsonElement timeElement = entry.get("time");
            if (timeElement == null) {
                continue;
            }
            String timeStr = timeElement.getAsString();

            try {
                LocalDateTime entryTime = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                long diff = Math.abs(java.time.Duration.between(now, entryTime).toMinutes());

                if (diff < smallestDiff) {
                    smallestDiff = diff;
                    bestMatch = entry;
                }
            } catch (Exception e) {
                // Skip entries with invalid time format
            }
        }

        if (bestMatch == null) {
            // Fallback to first entry
            return forecastArray.get(0).getAsJsonObject();
        }

        return bestMatch;
    }

    private double getNestedDouble(JsonObject obj, String field, String nestedKey) {
        JsonElement fieldElement = obj.get(field);
        if (fieldElement == null || !fieldElement.isJsonObject()) {
            throw new RuntimeException(field + " not found in forecast data");
        }
        JsonObject nested = fieldElement.getAsJsonObject();
        JsonElement value = nested.get(nestedKey);
        if (value == null) {
            throw new RuntimeException(nestedKey + " not found in " + field);
        }
        return value.getAsDouble();
    }

    private String formatTimestamp() {
        return ZonedDateTime.now(ZoneId.of("Europe/Madrid"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Override
    protected String getUrl(int wgId) {
        return SPOTFAV_URL;
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
    }
}