package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditions;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches current conditions for Turawa Lake available
 * on the <a href="https://airmax.pl/kamery/turawa">Airmax</a> website.
 */
public class FetchCurrentConditionsStrategyTurawa extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {
    private static final int TURAWA_WG_ID = 726;
    private static final String TURAWA_LIVE_URL = "https://airmax.pl/kamery/turawa";

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("padding-top:10px.*?>(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})");
    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("font-size: 16px;'>(\\d+)&deg;C");
    private static final Pattern WIND_SPEED_PATTERN = Pattern.compile("wind_speed\\.png.*?padding-right: 10px;'>(\\d+\\.\\d+)\\s+m/s");
    private static final Pattern WIND_DIRECTION_PATTERN = Pattern.compile("wind_rose\\.png.*?padding-right: 10px;'>(\\d+)\\s+&deg;");

    private static final double MS_TO_KNOTS = 1.94384;

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
    protected Mono<CurrentConditions> fetchCurrentConditions(String url) {
        return Mono.fromCallable(() -> {
            Request request = new Request
                    .Builder()
                    .url(url)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to fetch current conditions: " + response);
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new RuntimeException("Failed to fetch current conditions: response body is null");
                }
                return createCurrentConditions(responseBody);
            }
        });
    }

    private CurrentConditions createCurrentConditions(ResponseBody responseBody) throws IOException {
        String body = responseBody.string();
        String timestamp = extractValue(body, TIMESTAMP_PATTERN, "timestamp");
        int temperature = (int) Math.round(Double.parseDouble(extractValue(body, TEMPERATURE_PATTERN, "temperature")));
        double windSpeedMs = Double.parseDouble(extractValue(body, WIND_SPEED_PATTERN, "wind speed"));
        int windSpeedKnots = (int) Math.round(windSpeedMs * MS_TO_KNOTS);
        int windDirectionDegrees = Integer.parseInt(extractValue(body, WIND_DIRECTION_PATTERN, "wind direction"));
        String windDirection = windDirectionDegreesToCardinal(windDirectionDegrees);
        return new CurrentConditions(timestamp, windSpeedKnots, 0, windDirection, temperature);
    }

    @Override
    protected String getUrl(int wgId) {
        return TURAWA_LIVE_URL;
    }

    private String extractValue(String body, Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new RuntimeException("Could not extract " + fieldName + " from HTML");
        }
        return matcher.group(1);
    }
}
