package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.mapper.WeatherForecastMapper;
import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.ForecastData;
import com.github.pwittchen.varun.model.forecast.ForecastModel;
import com.github.pwittchen.varun.model.forecast.ForecastWg;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ForecastService {
    // for help regarding website usage, visit: https://micro.windguru.cz/help.php
    private static final String URL = "https://micro.windguru.cz";
    private static final String FORECAST_PARAMS = "WSPD,GUST,WDEG,TMP,APCP1,HCLD,MCLD,LCLD,SLP";
    private static final String WAVE_PARAMS = "HTSGW,PERPW,WADEG";
    private static final String WAVE_MODEL = "ewam";

    private final OkHttpClient httpClient;
    private final WeatherForecastMapper mapper;

    public ForecastService(WeatherForecastMapper mapper, OkHttpClient httpClient) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public Mono<ForecastData> getForecastData(int wgSpotId) {
        return getForecastData(wgSpotId, ForecastModel.GFS);
    }

    public Mono<ForecastData> getForecastData(int wgSpotId, ForecastModel forecastModel) {
        final HttpUrl httpUrl = HttpUrl.parse(URL);
        if (httpUrl == null) return Mono.just(new ForecastData(List.of(), Map.of()));

        Mono<List<ForecastWg>> forecastMono = executeHttpRequest(new Request
                .Builder()
                .url(httpUrl
                        .newBuilder()
                        .addQueryParameter("s", String.valueOf(wgSpotId))
                        .addQueryParameter("m", forecastModel.modelKey())
                        .addQueryParameter("v", FORECAST_PARAMS)
                        .build()
                        .toString())
                .get()
                .build())
                .map(this::retrieveWgForecasts);

        Mono<Map<String, WaveData>> waveMono = fetchWaveData(wgSpotId);

        return Mono.zip(forecastMono, waveMono)
                .map(tuple -> {
                    List<ForecastWg> forecasts = tuple.getT1();
                    Map<String, WaveData> waveByLabel = tuple.getT2();
                    List<ForecastWg> merged = mergeWaveData(forecasts, waveByLabel);
                    return new ForecastData(
                            mapper.toWeatherForecasts(merged),
                            Map.of(forecastModel, mapper.toHourlyForecasts(merged))
                    );
                })
                .onErrorResume(_ -> forecastMono.map(forecasts -> new ForecastData(
                        mapper.toWeatherForecasts(forecasts),
                        Map.of(forecastModel, mapper.toHourlyForecasts(forecasts))
                )));
    }

    public Mono<List<Forecast>> getForecast(int wgSpotId) {
        return getForecastData(wgSpotId).map(ForecastData::daily);
    }

    private record WaveData(Double height, Double period, Integer directionDeg) {}

    private Mono<Map<String, WaveData>> fetchWaveData(int wgSpotId) {
        final HttpUrl httpUrl = HttpUrl.parse(URL);
        if (httpUrl == null) return Mono.just(Map.of());
        return executeHttpRequest(new Request
                .Builder()
                .url(httpUrl
                        .newBuilder()
                        .addQueryParameter("s", String.valueOf(wgSpotId))
                        .addQueryParameter("m", WAVE_MODEL)
                        .addQueryParameter("v", WAVE_PARAMS)
                        .build()
                        .toString())
                .get()
                .build())
                .map(this::retrieveWaveData)
                .onErrorResume(_ -> Mono.just(Map.of()));
    }

    private Map<String, WaveData> retrieveWaveData(String microText) {
        String[] lines = microText.split("\\r?\\n");

        // Wave-only format: " Fri 20. 13h     0.1       2      42"
        Pattern row = Pattern.compile(
                "^\\s*" +
                        "(Mon|Tue|Wed|Thu|Fri|Sat|Sun)" +
                        "\\s+(\\d{1,2})\\.\\s+(\\d{2})h\\s+" +
                        "(-|\\d+(?:\\.\\d+)?)\\s+" +       // HTSGW
                        "(-|\\d+(?:\\.\\d+)?)\\s+" +       // PERPW
                        "(-|\\d+(?:\\.\\d+)?)\\s*$"        // WADEG
        );

        Map<String, WaveData> result = new java.util.LinkedHashMap<>();
        for (String line : lines) {
            line = line.trim().replace('\u00A0', ' ');
            Matcher m = row.matcher(line);
            if (m.find()) {
                String label = String.format("%s %s. %sh", m.group(1), m.group(2), m.group(3));
                result.put(label, new WaveData(
                        parseNullableDouble(m.group(4)),
                        parseNullableDouble(m.group(5)),
                        parseNullableInt(m.group(6))
                ));
            }
        }
        return result;
    }

    private List<ForecastWg> mergeWaveData(List<ForecastWg> forecasts, Map<String, WaveData> waveByLabel) {
        if (waveByLabel.isEmpty()) return forecasts;
        return forecasts.stream()
                .map(f -> {
                    WaveData wave = waveByLabel.get(f.label());
                    if (wave != null) {
                        return new ForecastWg(
                                f.label(), f.windSpeed(), f.gust(), f.windDirectionDegrees(),
                                f.temperature(), f.apcpMm1h(), f.cloudCoverPercent(), f.pressureHpa(),
                                wave.height(), wave.period(), wave.directionDeg()
                        );
                    }
                    return f;
                })
                .collect(Collectors.toList());
    }

    private Mono<String> executeHttpRequest(final Request request) {
        return Mono.<String>create(sink -> {
            Call call = httpClient.newCall(request);
            sink.onCancel(call::cancel);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    sink.error(e);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    try (response) {
                        if (!response.isSuccessful()) {
                            sink.error(new IOException("HTTP " + response.code() + ": " + response.message()));
                            return;
                        }
                        ResponseBody body = response.body();
                        sink.success(body != null ? body.string() : "");
                    } catch (Exception e) {
                        sink.error(e);
                    }
                }
            });
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private List<ForecastWg> retrieveWgForecasts(final String microText) {
        String[] lines = microText.split("\\r?\\n");

        // Example line:
        // " Mon 29. 02h      15      20     257      20       -      80      60      40    1013"
        Pattern row = Pattern.compile(
                "^\\s*" +                                      // leading spaces
                        "(Mon|Tue|Wed|Thu|Fri|Sat|Sun)" +      // weekday
                        "\\s+(\\d{1,2})\\.\\s+(\\d{2})h\\s+" + // day of month + hour
                        "(-?\\d+)\\s+" +                       // WSPD
                        "(-?\\d+)\\s+" +                       // GUST
                        "(-?\\d+)\\s+" +                       // WDEG  (degrees)
                        "(-?\\d+)\\s+" +                       // TMP   (C)
                        "(-|\\d+(?:\\.\\d+)?)\\s+" +           // APCP1 (mm/1h or '-')
                        "(-|\\d+)\\s+" +                       // HCLD  (high clouds %)
                        "(-|\\d+)\\s+" +                       // MCLD  (mid clouds %)
                        "(-|\\d+)\\s+" +                       // LCLD  (low clouds %)
                        "(-|\\d+(?:\\.\\d+)?)\\s*$"            // SLP   (sea-level pressure hPa)
        );

        return Arrays.stream(lines)
                .map(line -> parseLineToForecast(line, row))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private Optional<ForecastWg> parseLineToForecast(String line, Pattern row) {
        line = line.trim().replace('\u00A0', ' '); // non-breaking spaces → space
        Matcher m = row.matcher(line);
        if (m.find()) return Optional.of(createForecast(m));
        return Optional.empty();
    }

    private ForecastWg createForecast(Matcher m) {
        String label = String.format("%s %s. %sh", m.group(1), m.group(2), m.group(3));
        int hcld = parseNumber(m.group(9)).intValue();
        int mcld = parseNumber(m.group(10)).intValue();
        int lcld = parseNumber(m.group(11)).intValue();
        int cloudCover = Math.max(hcld, Math.max(mcld, lcld));
        return new ForecastWg(
                label,
                parseNumber(m.group(4)).intValue(),
                parseNumber(m.group(5)).intValue(),
                parseNumber(m.group(6)).intValue(),
                parseNumber(m.group(7)).intValue(),
                parseNumber(m.group(8)).intValue(),
                cloudCover,
                parseNumber(m.group(12)).intValue()
        );
    }

    private Number parseNumber(String s) {
        if (s == null || s.equals("-")) return 0;
        try {
            if (s.contains(".")) return Double.parseDouble(s);
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Double parseNullableDouble(String s) {
        if (s == null || s.equals("-")) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseNullableInt(String s) {
        if (s == null || s.equals("-")) return null;
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
