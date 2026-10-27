package com.github.pwittchen.varun.service.live.strategy;

import static com.github.pwittchen.varun.config.OkHttpClientConfig.LIVE_STATIONS_HTTP_CLIENT;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditions;
import com.github.pwittchen.varun.service.live.FetchCurrentConditionsStrategyBase;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy for fetching current conditions for the southern shore of Turawa Lake
 * from the ISZCZE187 personal weather station in Szczedrzyk, read from its public
 * <a href="https://www.wunderground.com/dashboard/pws/ISZCZE187">Weather Underground dashboard</a>.
 * The page is rendered on the server with the latest observation in the attributes
 * of its widgets ({@code <wind-widget-view data-wind-speed="...">} and so on), so no
 * API key is needed. The station sits on the southern shore, so it serves the
 * "Turawa Południe" spot, while the northern shore ("Turawa Północ") is covered by
 * {@link FetchCurrentConditionsStrategyTurawa} (airmax.pl).
 */
@Component
public class FetchCurrentConditionsStrategyTurawaWunderground extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    // generated ID of the "Turawa Południe" spot,
    // which shares the Windguru forecast point with "Turawa Północ"
    private static final int TURAWA_SOUTH_WG_ID = 9387270;
    private static final String DASHBOARD_URL = "https://www.wunderground.com/dashboard/pws/ISZCZE187";

    private static final Pattern WIND_WIDGET_PATTERN = Pattern.compile("<wind-widget-view\\b[^>]*>");
    private static final Pattern TEMP_WIDGET_PATTERN = Pattern.compile("<temp-widget-view\\b[^>]*>");
    private static final Pattern OBS_TIME_PATTERN = Pattern.compile("data-obs-time-utc=\"([^\"]+)\"");

    // e.g. "Tue Oct 27 2026 10:06:19 GMT+0000 (Coordinated Universal Time)"
    private static final DateTimeFormatter OBS_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'xx", Locale.ENGLISH);
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId STATION_ZONE = ZoneId.of("Europe/Warsaw");

    // the dashboard is served in metric units ("m"), where wind is in km/h
    private static final String METRIC_UNIT = "m";
    private static final double KMH_TO_KNOTS = 0.539957;

    private final OkHttpClient httpClient;

    public FetchCurrentConditionsStrategyTurawaWunderground(@Qualifier(LIVE_STATIONS_HTTP_CLIENT) OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == TURAWA_SOUTH_WG_ID;
    }

    @Override
    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        var url = getUrl(wgId);
        return fetchCurrentConditions(url);
    }

    @Override
    protected String getUrl(int wgId) {
        return DASHBOARD_URL;
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
        String windWidget = extractTag(body, WIND_WIDGET_PATTERN, "wind widget");
        String tempWidget = extractTag(body, TEMP_WIDGET_PATTERN, "temperature widget");

        String unit = extractAttribute(windWidget, "data-unit", "unit");
        if (!METRIC_UNIT.equals(unit)) {
            throw new RuntimeException("Unexpected unit system in response: " + unit);
        }

        String timestamp = parseObservationTime(extractValue(body, OBS_TIME_PATTERN, "observation time"));
        int windDirectionDegrees = (int) Math.round(extractDouble(windWidget, "data-wind-dir", "wind direction"));
        String windDirection = windDirectionDegreesToCardinal(windDirectionDegrees);
        int wind = (int) Math.round(extractDouble(windWidget, "data-wind-speed", "wind speed") * KMH_TO_KNOTS);
        int gusts = (int) Math.round(extractDouble(windWidget, "data-wind-gust", "wind gusts") * KMH_TO_KNOTS);
        int temperature = (int) Math.round(extractDouble(tempWidget, "data-temp", "temperature"));

        return new CurrentConditions(timestamp, wind, gusts, windDirection, temperature);
    }

    private String parseObservationTime(String rawTime) {
        // drop the trailing "(Coordinated Universal Time)" zone name
        String time = rawTime.replaceAll("\\s*\\(.*\\)$", "");
        try {
            return ZonedDateTime.parse(time, OBS_TIME_FORMATTER)
                    .withZoneSameInstant(STATION_ZONE)
                    .format(OUTPUT_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new RuntimeException("Could not parse observation time: " + rawTime, e);
        }
    }

    private String extractTag(String body, Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new RuntimeException("Could not extract " + fieldName + " from HTML");
        }
        return matcher.group();
    }

    private double extractDouble(String tag, String attribute, String fieldName) {
        String value = extractAttribute(tag, attribute, fieldName);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Could not extract " + fieldName + " from HTML", e);
        }
    }

    private String extractAttribute(String tag, String attribute, String fieldName) {
        return extractValue(tag, Pattern.compile(attribute + "=\"([^\"]*)\""), fieldName);
    }

    private String extractValue(String body, Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new RuntimeException("Could not extract " + fieldName + " from HTML");
        }
        return matcher.group(1);
    }
}
