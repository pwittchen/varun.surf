package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.CurrentConditions;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class CurrentConditionsService {
    private static final List<String> WIND_DIRECTIONS = Arrays.asList("N", "NE", "E", "SE", "S", "SW", "W", "NW");
    private static final Map<Integer, String> LIVE_CONDITIONS_URLS = Map.of(
            126330, "https://www.wiatrkadyny.pl/wiatrkadyny.txt",
            14473, "https://www.wiatrkadyny.pl/krynica/wiatrkadyny.txt",
            509469, "https://www.wiatrkadyny.pl/kuznica/wiatrkadyny.txt",
            500760, "https://www.wiatrkadyny.pl/draga/wiatrkadyny.txt",
            4165, "https://www.wiatrkadyny.pl/rewa/wiatrkadyny.txt",
            48009, "https://www.wiatrkadyny.pl/puck/wiatrkadyny.txt"


    );
    private static final String KITERIDERS_AT_URL = "https://www.kiteriders.at/wind/weatherstat_kn.html";
    private static final int PODERSDORF_WG_ID = 859182;

    private final OkHttpClient httpClient = new OkHttpClient();

    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        if (LIVE_CONDITIONS_URLS.containsKey(wgId)) {
            return fetchWiatrKadynyForecast(LIVE_CONDITIONS_URLS.get(wgId));
        } else if (wgId == PODERSDORF_WG_ID) {
            return fetchPodersdorfForecast();
        }
        return Mono.empty();
    }

    Mono<CurrentConditions> fetchWiatrKadynyForecast(String url) {
        return Mono.fromCallable(() -> {
            Request request = new Request
                    .Builder()
                    .url(url)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to fetch forecast: " + response);
                }

                ResponseBody responseBody = response.body();

                if (responseBody == null) {
                    throw new RuntimeException("Failed to fetch forecast: " + response);
                }

                String body = responseBody.string();
                String[] parts = body.trim().split("\\s+");

                String date = parts[0] + " " + parts[1];
                int temp = (int) Math.round(Double.parseDouble(parts[2]));
                int wind = (int) Math.round(Double.parseDouble(parts[6]));
                String direction = normalizeDirection(parts[11]);
                int gusts = (int) Math.round(Double.parseDouble(parts[26]));

                return new CurrentConditions(date, wind, gusts, direction, temp);
            }
        });
    }

    private String normalizeDirection(String rawDirection) {
        if (WIND_DIRECTIONS.contains(rawDirection)) {
            return rawDirection;
        }

        return switch (rawDirection.toUpperCase()) {
            case "NNE", "ENE" -> "NE";
            case "ESE", "SSE" -> "SE";
            case "SSW", "WSW" -> "SW";
            case "WNW", "NNW" -> "NW";
            default -> findClosestDirection(rawDirection);
        };
    }

    private String findClosestDirection(String rawDirection) {
        for (String direction : WIND_DIRECTIONS) {
            if (rawDirection.toUpperCase().startsWith(direction)) {
                return direction;
            }
        }
        return "N";
    }

    public Mono<CurrentConditions> fetchPodersdorfForecast() {
        return fetchPodersdorfForecast(KITERIDERS_AT_URL);
    }

    Mono<CurrentConditions> fetchPodersdorfForecast(String url) {
        return Mono.fromCallable(() -> {
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to fetch forecast: " + response);
                }

                ResponseBody responseBody = response.body();

                if (responseBody == null) {
                    throw new RuntimeException("Failed to fetch forecast: " + response);
                }

                String body = responseBody.string();
                String[] lines = body.split("\n");

                String dataRow = null;

                int trCount = 0;
                for (String line : lines) {
                    if (line.trim().startsWith("<tr")) {
                        trCount++;
                        if (trCount == 2) {
                            dataRow = line;
                            break;
                        }
                    }
                }

                if (dataRow == null) {
                    throw new RuntimeException("No data row found");
                }

                String[] cells = dataRow.split("</td>");

                String dateCell = extractTextFromTd(cells[0]);
                String timeCell = extractTextFromTd(cells[1]);
                String directionCell = extractTextFromTd(cells[2]);
                String windCell = extractTextFromTd(cells[4]);
                String gustCell = extractTextFromTd(cells[6]);
                String tempCell = extractTextFromTd(cells[7]);

                String date = dateCell + " " + timeCell;
                String direction = normalizeDirection(directionCell);
                int wind = (int) Math.round(parseKnots(windCell));
                int gusts = (int) Math.round(parseKnots(gustCell));
                int temp = (int) Math.round(parseTemperature(tempCell));

                return new CurrentConditions(date, wind, gusts, direction, temp);
            }
        });
    }

    private String extractTextFromTd(String tdContent) {
        String text = tdContent.replaceAll("<[^>]*>", "").trim();
        text = text.replace("&nbsp;", "");
        return text.trim();
    }

    private double parseKnots(String value) {
        return Double.parseDouble(value.replace("kn", "").trim());
    }

    private double parseTemperature(String value) {
        return Double.parseDouble((value.split(" ")[0]).trim());
    }
}