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
 * Fetches current conditions for the Silvaplana spot on the Silvaplanersee (Switzerland)
 * from the Kitesailing webcam page available at
 * <a href="https://www.kitesailing.ch/spot/webcam">kitesailing.ch/spot/webcam</a>.
 * The page server-renders a LiveMeteo weather widget with the latest station reading, e.g.:
 * <pre>
 * &lt;div class="lmw-weather-today-name"&gt;Sonntag, 28.6.2026 (09:50:00)&lt;/div&gt;
 * &lt;div class="lmw-weather-today-temp"&gt;16.6 °C&lt;/div&gt;
 * ... Windspitzen ... &lt;div class="lmw-weather-today-wind"&gt;5 km/h &lt;small&gt;(2.5 kn)&lt;/small&gt;&lt;/div&gt;
 * Windrichtung: SO (135°)
 * Mittelwind: 3 km/h (1 Bft)
 * </pre>
 * Wind speeds are reported in km/h and converted to knots.
 */
@Component
public class FetchCurrentConditionsStrategySilvaplana extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int SILVAPLANA_WG_ID = 1584;
    private static final String WEBCAM_URL = "https://www.kitesailing.ch/spot/webcam";
    private static final double KMH_TO_KNOTS = 0.539957;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "lmw-weather-today-name\"[^>]*>[^,<]*,\\s*(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\s*\\((\\d{1,2}):(\\d{2}):(\\d{2})\\)");
    private static final Pattern TEMP_PATTERN = Pattern.compile(
            "lmw-weather-today-temp\"[^>]*>\\s*([\\d.,]+)\\s*°C");
    private static final Pattern GUST_PATTERN = Pattern.compile(
            "Windspitzen.*?lmw-weather-today-wind\"[^>]*>\\s*([\\d.,]+)\\s*km/h", Pattern.DOTALL);
    private static final Pattern WIND_PATTERN = Pattern.compile(
            "Mittelwind:\\s*([\\d.,]+)\\s*km/h");
    private static final Pattern DIRECTION_PATTERN = Pattern.compile(
            "Windrichtung:\\s*[^(<]*\\((\\d+)\\s*°\\)");

    private final OkHttpClient httpClient;

    public FetchCurrentConditionsStrategySilvaplana(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == SILVAPLANA_WG_ID;
    }

    @Override
    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        return fetchCurrentConditions(getUrl(wgId));
    }

    @Override
    protected Mono<CurrentConditions> fetchCurrentConditions(String url) {
        return Mono.fromCallable(() -> {
            Request request = new Request.Builder().url(url).build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to fetch current conditions: " + response);
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new RuntimeException("Failed to fetch current conditions: response body is null");
                }

                return parseConditions(responseBody.string());
            }
        });
    }

    private CurrentConditions parseConditions(String html) {
        int wind = (int) Math.round(parseKmh(WIND_PATTERN, html) * KMH_TO_KNOTS);
        int gusts = (int) Math.round(parseKmh(GUST_PATTERN, html) * KMH_TO_KNOTS);
        int directionDeg = parseDirectionDegrees(html);
        String direction = windDirectionDegreesToCardinal(directionDeg);
        int temp = (int) Math.round(parseTemperature(html));
        String timestamp = parseTimestamp(html);

        return new CurrentConditions(timestamp, wind, gusts, direction, temp);
    }

    private double parseKmh(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            throw new RuntimeException("Could not find wind speed in response");
        }
        return parseDecimal(matcher.group(1));
    }

    private double parseTemperature(String html) {
        Matcher matcher = TEMP_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new RuntimeException("Could not find temperature in response");
        }
        return parseDecimal(matcher.group(1));
    }

    private int parseDirectionDegrees(String html) {
        Matcher matcher = DIRECTION_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new RuntimeException("Could not find wind direction in response");
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String parseTimestamp(String html) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new RuntimeException("Could not find timestamp in response");
        }
        LocalDateTime readingTime = LocalDateTime.of(
                Integer.parseInt(matcher.group(3)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(4)),
                Integer.parseInt(matcher.group(5)),
                Integer.parseInt(matcher.group(6)));
        return readingTime.format(TIMESTAMP_FORMATTER);
    }

    private double parseDecimal(String value) {
        return Double.parseDouble(value.trim().replace(',', '.'));
    }

    @Override
    protected String getUrl(int wgId) {
        return WEBCAM_URL;
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
    }
}
