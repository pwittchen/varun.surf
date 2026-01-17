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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy for fetching current conditions from the Mietków spot in Poland
 * using the WeeWX weather station at frog01-21064.wykr.es
 */
@Component
public class FetchCurrentConditionsStrategyMietkow extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int MIETKOW_WG_ID = 304;
    private static final String MIETKOW_LIVE_READINGS = "https://frog01-21064.wykr.es/weewx/inx.html";

    // Pattern to extract wind speed and gust: "2 knt (Max 3)"
    private static final Pattern WIND_PATTERN = Pattern.compile(
            "<td class=\"label\"[^>]*>Wiatr</td>\\s*<td class=\"data\"[^>]*>(\\d+)\\s*knt\\s*\\(Max\\s*(\\d+)\\)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract wind direction from arrow rotation: "transform:rotate(calc(50.47deg - 180deg))"
    // We want the first arrow which represents current wind direction
    private static final Pattern DIRECTION_PATTERN = Pattern.compile(
            "transform:rotate\\(calc\\(([0-9.]+)deg\\s*-\\s*180deg\\)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract temperature: "<td class="label">Temperatura</td><td class="data">1,7°C</td>"
    private static final Pattern TEMP_PATTERN = Pattern.compile(
            "<td class=\"label\">Temperatura</td>\\s*<td class=\"data\">([0-9,.-]+)&#176;C</td>",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract timestamp: "17-sty-2026 17:22:24"
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "<span id=\"lastupdate_value\">([^<]+)</span>",
            Pattern.CASE_INSENSITIVE
    );

    // Formatter for parsing Polish month abbreviations
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern(
            "dd-MMM-yyyy HH:mm:ss",
            new Locale("pl", "PL")
    );

    // Formatter for output timestamp
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OkHttpClient httpClient;

    public FetchCurrentConditionsStrategyMietkow(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == MIETKOW_WG_ID;
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
                return parseResponse(body);
            }
        });
    }

    private CurrentConditions parseResponse(String html) {
        // Extract wind speed and gust
        Matcher windMatcher = WIND_PATTERN.matcher(html);
        if (!windMatcher.find()) {
            throw new RuntimeException("Could not find wind data in HTML");
        }
        int windSpeed = Integer.parseInt(windMatcher.group(1));
        int windGust = Integer.parseInt(windMatcher.group(2));

        // Extract wind direction from first arrow rotation
        Matcher directionMatcher = DIRECTION_PATTERN.matcher(html);
        if (!directionMatcher.find()) {
            throw new RuntimeException("Could not find wind direction in HTML");
        }
        double rotationDeg = Double.parseDouble(directionMatcher.group(1));
        // The rotation is already the wind direction in degrees (before -180deg adjustment)
        int windDirectionDeg = (int) Math.round(rotationDeg);
        String windDirection = windDirectionDegreesToCardinal(windDirectionDeg);

        // Extract temperature
        Matcher tempMatcher = TEMP_PATTERN.matcher(html);
        if (!tempMatcher.find()) {
            throw new RuntimeException("Could not find temperature in HTML");
        }
        String tempStr = tempMatcher.group(1).replace(",", ".");
        int temp = (int) Math.round(Double.parseDouble(tempStr));

        // Extract and format timestamp
        Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(html);
        String timestamp;
        if (timestampMatcher.find()) {
            String rawTimestamp = timestampMatcher.group(1);
            try {
                // Parse Polish date format and convert to standard format
                ZonedDateTime dateTime = ZonedDateTime.parse(rawTimestamp, INPUT_FORMATTER.withZone(ZoneId.of("Europe/Warsaw")));
                timestamp = dateTime.format(OUTPUT_FORMATTER);
            } catch (Exception e) {
                // Fallback to current timestamp if parsing fails
                timestamp = ZonedDateTime.now(ZoneId.of("Europe/Warsaw")).format(OUTPUT_FORMATTER);
            }
        } else {
            // Fallback to current timestamp
            timestamp = ZonedDateTime.now(ZoneId.of("Europe/Warsaw")).format(OUTPUT_FORMATTER);
        }

        return new CurrentConditions(timestamp, windSpeed, windGust, windDirection, temp);
    }

    @Override
    protected String getUrl(int wgId) {
        return MIETKOW_LIVE_READINGS;
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
    }
}