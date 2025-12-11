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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is specifically designed to retrieve data for the MB weather station located on Góra Żar
 * and uses Holfuy weather station HTML page to fetch live readings from the 15-minute averages table.
 */
@Component
public class FetchCurrentConditionsStrategyMB extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {
    private static final int MB_WG_ID = 1068590;
    private static final int HOLFUY_STATION_ID = 1612;
    private static final String HOLFUY_PAGE_URL = "https://holfuy.com/en/weather/" + HOLFUY_STATION_ID;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Pattern to extract wind speed values from the first table row (Speed row)
    // Matches: <td class="h_header" ...>Speed</td>...<td...>value</td>...</tr>
    private static final Pattern SPEED_ROW_PATTERN = Pattern.compile(
            ">Speed</td>(.*?)</tr>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract gust values from the Gust row
    // Note: Header cell contains <span> element, so we match until </td> allowing any content
    private static final Pattern GUST_ROW_PATTERN = Pattern.compile(
            ">Gust.*?</td>(.*?)</tr>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract direction degrees from the Direction Deg row
    private static final Pattern DIRECTION_ROW_PATTERN = Pattern.compile(
            ">Direction<br>.*?Deg\\..*?</td>(.*?)</tr>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract temperature values from the Temp row
    // Note: Header cell contains <span> element, so we match until </td> allowing any content
    private static final Pattern TEMP_ROW_PATTERN = Pattern.compile(
            ">Temp\\..*?</td>(.*?)</tr>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract numeric values from table cells
    private static final Pattern CELL_VALUE_PATTERN = Pattern.compile(
            "<td[^>]*>([0-9.]+)</td>"
    );

    // Pattern to extract direction with degrees from cells like "NE<br>34°"
    private static final Pattern DIRECTION_CELL_PATTERN = Pattern.compile(
            "<td>([A-Z]+)<br>(\\d+)°</td>"
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
        return HOLFUY_PAGE_URL;
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
        // Remove all newlines and extra whitespace to simplify parsing
        String body = responseBody.string().replaceAll("\\s+", " ");

        // Extract wind speed (last value from first Speed row in first table)
        double windSpeedMs = extractLastValueFromRow(body, SPEED_ROW_PATTERN);

        // Extract wind gust (last value from Gust row)
        double windGustMs = extractLastValueFromRow(body, GUST_ROW_PATTERN);

        // Extract wind direction degrees (last value from Direction Deg row)
        int windDirectionDeg = extractLastDirectionDegrees(body);

        // Extract temperature (last value from Temp row)
        double temperature = extractLastValueFromRow(body, TEMP_ROW_PATTERN);

        int windSpeedKnots = (int) Math.round(windSpeedMs * MS_TO_KNOTS);
        int windGustsKnots = (int) Math.round(windGustMs * MS_TO_KNOTS);
        int tempC = (int) Math.round(temperature);
        String windDirection = windDirectionDegreesToCardinal(windDirectionDeg);
        String timestamp = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"))
                .format(TIMESTAMP_FORMATTER);

        return new CurrentConditions(timestamp, windSpeedKnots, windGustsKnots, windDirection, tempC);
    }

    private double extractLastValueFromRow(String html, Pattern rowPattern) {
        Matcher rowMatcher = rowPattern.matcher(html);
        if (!rowMatcher.find()) {
            throw new RuntimeException("Could not find row in HTML");
        }

        String rowContent = rowMatcher.group(1);
        List<Double> values = new ArrayList<>();

        Matcher cellMatcher = CELL_VALUE_PATTERN.matcher(rowContent);
        while (cellMatcher.find()) {
            values.add(Double.parseDouble(cellMatcher.group(1)));
        }

        if (values.isEmpty()) {
            throw new RuntimeException("No values found in row");
        }

        return values.getLast();
    }

    private int extractLastDirectionDegrees(String html) {
        Matcher rowMatcher = DIRECTION_ROW_PATTERN.matcher(html);
        if (!rowMatcher.find()) {
            throw new RuntimeException("Could not find direction row in HTML");
        }

        String rowContent = rowMatcher.group(1);
        List<Integer> degrees = new ArrayList<>();

        Matcher cellMatcher = DIRECTION_CELL_PATTERN.matcher(rowContent);
        while (cellMatcher.find()) {
            degrees.add(Integer.parseInt(cellMatcher.group(2)));
        }

        if (degrees.isEmpty()) {
            throw new RuntimeException("No direction values found in row");
        }

        return degrees.getLast();
    }
}
