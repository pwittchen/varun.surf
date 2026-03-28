package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditionsStrategyBase;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy for fetching current conditions from Le Barcarès (France) via Winds-Up station.
 * Data source: https://m.winds-up.com/spot/58
 */
@Component
public class FetchCurrentConditionsStrategyLeBarcares extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int LE_BARCARES_WG_ID = 1146458;
    private static final String WINDS_UP_SPOT_58_URL = "https://m.winds-up.com/spot/58";

    // Pattern to extract current wind data from embedded JavaScript
    // Format: data: [{x:1774682280000,y:31,o:"NO",color:"#EC6E0F",img:"//img.winds-up.com/maps/new/anemo_30-NO.gif",}
    private static final Pattern CURRENT_DATA_PATTERN = Pattern.compile(
            "data:\\s*\\[\\{x:(\\d+),y:(\\d+),o:\"([^\"]+)\""
    );

    // Pattern to extract min/max (gusts) data
    // Format: data: [{x:1774682280000,low:28,high:35,}
    private static final Pattern GUST_DATA_PATTERN = Pattern.compile(
            "data:\\s*\\[\\{x:(\\d+),low:(\\d+),high:(\\d+),"
    );

    private final OkHttpClient httpClient;

    public FetchCurrentConditionsStrategyLeBarcares(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == LE_BARCARES_WG_ID;
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
                return parseJavaScriptData(body);
            }
        });
    }

    private CurrentConditions parseJavaScriptData(String html) {
        // Extract current wind data (first occurrence)
        Matcher currentMatcher = CURRENT_DATA_PATTERN.matcher(html);
        if (!currentMatcher.find()) {
            throw new RuntimeException("Current wind data not found in response");
        }

        long timestampMs = Long.parseLong(currentMatcher.group(1));
        int windSpeed = Integer.parseInt(currentMatcher.group(2));
        String rawDirection = currentMatcher.group(3);
        String direction = normalizeFrenchDirection(rawDirection);

        // Extract gust data (first occurrence after current data)
        Matcher gustMatcher = GUST_DATA_PATTERN.matcher(html);
        int windGusts = windSpeed; // Default to wind speed if no gust data
        if (gustMatcher.find()) {
            windGusts = Integer.parseInt(gustMatcher.group(3)); // high value
        }

        // Convert timestamp to formatted string (Europe/Paris timezone for France)
        String timestamp = formatTimestamp(timestampMs);

        // Winds-Up doesn't provide temperature data, use 0 as placeholder
        int temp = 0;

        return new CurrentConditions(timestamp, windSpeed, windGusts, direction, temp);
    }

    /**
     * Normalizes French wind direction abbreviations to English cardinal directions.
     * French: N, NE, E, SE, S, SO (Sud-Ouest = SW), O (Ouest = W), NO (Nord-Ouest = NW)
     */
    private String normalizeFrenchDirection(String frenchDirection) {
        return switch (frenchDirection.toUpperCase()) {
            case "NO" -> "NW";  // Nord-Ouest
            case "SO" -> "SW";  // Sud-Ouest
            case "O" -> "W";    // Ouest
            default -> normalizeDirection(frenchDirection);
        };
    }

    private String formatTimestamp(long timestampMs) {
        Instant instant = Instant.ofEpochMilli(timestampMs);
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("Europe/Paris"));
        return formatter.format(instant);
    }

    @Override
    protected String getUrl(int wgId) {
        return WINDS_UP_SPOT_58_URL;
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
    }
}