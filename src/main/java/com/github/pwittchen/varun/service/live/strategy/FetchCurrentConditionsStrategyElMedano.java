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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy for fetching current conditions from the El Médano (Tenerife) Cabezo weather station
 * at cabezo.bergfex.at/wetterstation/
 */
@Component
public class FetchCurrentConditionsStrategyElMedano extends FetchCurrentConditionsStrategyBase implements FetchCurrentConditions {

    private static final int EL_MEDANO_WG_ID = 207008;
    private static final String CABEZO_WEATHER_STATION_URL = "https://cabezo.bergfex.at/wetterstation/";

    // Regex patterns to extract data from HTML table
    private static final Pattern TIME_PATTERN = Pattern.compile("<td>(\\d{2}:\\d{2})</td>");
    private static final Pattern WIND_PATTERN = Pattern.compile("<td class=\"[^\"]*knt(\\d+)\"[^>]*>");
    private static final Pattern DIRECTION_PATTERN = Pattern.compile("<strong><span title=\"\\d+\">([A-Z]+)</span></strong>");
    private static final Pattern TEMP_PATTERN = Pattern.compile("<td class=\"mobile-hidden\">([\\d.]+) &deg;C</td>");

    private final OkHttpClient httpClient;

    public FetchCurrentConditionsStrategyElMedano(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public boolean canProcess(int wgId) {
        return wgId == EL_MEDANO_WG_ID;
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
        // Find the first data row (most recent reading)
        String[] lines = html.split("\n");
        String dataRow = findFirstDataRow(lines);

        if (dataRow == null) {
            throw new RuntimeException("No data row found in HTML response");
        }

        // Parse time
        Matcher timeMatcher = TIME_PATTERN.matcher(dataRow);
        if (!timeMatcher.find()) {
            throw new RuntimeException("Time not found in data row");
        }
        String time = timeMatcher.group(1);

        // Parse wind speed (first kntX class in the row)
        Matcher windMatcher = WIND_PATTERN.matcher(dataRow);
        if (!windMatcher.find()) {
            throw new RuntimeException("Wind speed not found in data row");
        }
        int windSpeed = Integer.parseInt(windMatcher.group(1));

        // Parse gusts (second kntX class in the row)
        if (!windMatcher.find()) {
            throw new RuntimeException("Wind gusts not found in data row");
        }
        int windGusts = Integer.parseInt(windMatcher.group(1));

        // Parse direction
        Matcher directionMatcher = DIRECTION_PATTERN.matcher(dataRow);
        if (!directionMatcher.find()) {
            throw new RuntimeException("Wind direction not found in data row");
        }
        String rawDirection = directionMatcher.group(1);
        String direction = normalizeDirection(rawDirection);

        // Parse temperature
        Matcher tempMatcher = TEMP_PATTERN.matcher(dataRow);
        if (!tempMatcher.find()) {
            throw new RuntimeException("Temperature not found in data row");
        }
        double tempValue = Double.parseDouble(tempMatcher.group(1));
        int temp = (int) Math.round(tempValue);

        // Create timestamp with today's date
        String timestamp = formatTimestamp(time);

        return new CurrentConditions(timestamp, windSpeed, windGusts, direction, temp);
    }

    private String findFirstDataRow(String[] lines) {
        // The first data row is the second <tr> in the table (first is header)
        int trCount = 0;
        StringBuilder rowBuilder = new StringBuilder();
        boolean inRow = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // Count <tr> tags (both with and without closing tags on same line)
            if (trimmedLine.startsWith("<tr")) {
                trCount++;
                if (trCount == 2) {
                    inRow = true;
                }
            }

            if (inRow) {
                rowBuilder.append(line).append("\n");
            }

            if (inRow && trimmedLine.endsWith("</tr>")) {
                return rowBuilder.toString();
            }
        }

        return null;
    }

    private String formatTimestamp(String time) {
        // Convert time to full timestamp with today's date
        // Format: "YYYY-MM-DD HH:MM:SS"
        LocalDate today = LocalDate.now();
        LocalTime localTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
        return String.format("%s %s:00",
                today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                localTime.format(DateTimeFormatter.ofPattern("HH:mm")));
    }

    @Override
    protected String getUrl(int wgId) {
        return CABEZO_WEATHER_STATION_URL;
    }

    @Override
    protected OkHttpClient getHttpClient() {
        return httpClient;
    }
}
