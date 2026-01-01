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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy for fetching current conditions from Spotfav Weatherflow stations.
 * Used for Tarifa, Arte Vida spot which has a Weatherflow Tempest station.
 */
@Component
public class FetchCurrentConditionsStrategyTarifaArteVida extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int TARIFA_ARTE_VIDA_WG_ID = 48775;
    private static final String SPOTFAV_URL = "https://www.spotfav.com/public/meteo/weatherflow-4eee927b185476763900001b/update/";

    // Regex patterns to extract data from HTML
    private static final Pattern WIND_DIRECTION_DEG_PATTERN = Pattern.compile("<div id='report_wind_direction'>\\s*(\\d+)\\s*º\\s*</div>");
    private static final Pattern WIND_SPEED_PATTERN = Pattern.compile("<div class='wind-speed'>\\s*(\\d+)\\s*knots\\s*</div>");
    private static final Pattern WIND_GUST_PATTERN = Pattern.compile("<div class='wind-gust'>\\s*(\\d+)\\s*knots\\s*</div>");
    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("<div class='temperature'>\\s*(\\d+)\\s*º\\s*C\\s*</div>");

    private final OkHttpClient httpClient;

    public FetchCurrentConditionsStrategyTarifaArteVida(OkHttpClient httpClient) {
        this.httpClient = httpClient;
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
                return parseHtmlResponse(body);
            }
        });
    }

    private CurrentConditions parseHtmlResponse(String html) {
        // Parse wind direction in degrees
        Matcher directionMatcher = WIND_DIRECTION_DEG_PATTERN.matcher(html);
        if (!directionMatcher.find()) {
            throw new RuntimeException("Wind direction not found in HTML response");
        }
        int directionDeg = Integer.parseInt(directionMatcher.group(1));
        String direction = windDirectionDegreesToCardinal(directionDeg);

        // Parse wind speed
        Matcher windSpeedMatcher = WIND_SPEED_PATTERN.matcher(html);
        if (!windSpeedMatcher.find()) {
            throw new RuntimeException("Wind speed not found in HTML response");
        }
        int windSpeed = Integer.parseInt(windSpeedMatcher.group(1));

        // Parse wind gusts
        Matcher gustMatcher = WIND_GUST_PATTERN.matcher(html);
        if (!gustMatcher.find()) {
            throw new RuntimeException("Wind gusts not found in HTML response");
        }
        int windGusts = Integer.parseInt(gustMatcher.group(1));

        // Parse temperature
        Matcher tempMatcher = TEMPERATURE_PATTERN.matcher(html);
        if (!tempMatcher.find()) {
            throw new RuntimeException("Temperature not found in HTML response");
        }
        int temp = Integer.parseInt(tempMatcher.group(1));

        // Create timestamp with current time
        String timestamp = formatTimestamp();

        return new CurrentConditions(timestamp, windSpeed, windGusts, direction, temp);
    }

    private String formatTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
