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

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is specifically designed to retrieve data for the MB weather station located on Góra Żar
 * and uses an external weather provider to fetch live readings.
 */
@Component
public class FetchCurrentConditionsStrategyMB extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {
    private static final int MB_WG_ID = 1068590;
    private static final String MB_LIVE_READINGS_URL = "https://pogoda.cc/pl/stacje/zar";

    private static final Pattern STATION_DATA_PATTERN = Pattern.compile(
            "#&lt;struct StationData.*?" +
            "epoch=(\\d+).*?" +
            "temperature=(-?[0-9]+\\.?[0-9]*).*?" +
            "winddir=(\\d+).*?" +
            "windgusts=([0-9]+\\.?[0-9]*).*?" +
            "windspeed=([0-9]+\\.?[0-9]*)"
    );

    private final OkHttpClient httpClient;

    public FetchCurrentConditionsStrategyMB(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == MB_WG_ID;
    }

    @Override
    protected String getUrl(int wgId) {
        return MB_LIVE_READINGS_URL;
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
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

            try (Response response = getHttpClient().newCall(request).execute()) {
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

        Matcher matcher = STATION_DATA_PATTERN.matcher(body);
        if (!matcher.find()) {
            throw new RuntimeException("Could not extract StationData from HTML");
        }

        long epoch = Long.parseLong(matcher.group(1));
        double temperature = Double.parseDouble(matcher.group(2));
        int windDirectionDeg = Integer.parseInt(matcher.group(3));
        double windGustsMs = Double.parseDouble(matcher.group(4));
        double windSpeedMs = Double.parseDouble(matcher.group(5));

        int windSpeedKnots = (int) Math.round(windSpeedMs * MS_TO_KNOTS);
        int windGustsKnots = (int) Math.round(windGustsMs * MS_TO_KNOTS);
        int tempC = (int) Math.round(temperature);
        String windDirection = windDirectionDegreesToCardinal(windDirectionDeg);
        String timestamp = java.time.Instant.ofEpochSecond(epoch)
                .atZone(java.time.ZoneId.of("Europe/Warsaw"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return new CurrentConditions(timestamp, windSpeedKnots, windGustsKnots, windDirection, tempC);
    }
}
