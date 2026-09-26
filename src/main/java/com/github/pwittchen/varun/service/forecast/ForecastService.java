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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ForecastService {
    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);
    // for help regarding website usage, visit: https://micro.windguru.cz/help.php
    private static final String URL = "https://micro.windguru.cz";
    private static final String FORECAST_PARAMS = "WSPD,GUST,WDEG,TMP,APCP1,HCLD,MCLD,LCLD,SLP";
    private static final String WAVE_PARAMS = "HTSGW,PERPW,WADEG";
    private static final String WAVE_MODEL = "ewam";
    private static final String USER_AGENT = "varun.surf (+https://varun.surf)";

    /**
     * How long every request to Windguru is held back after it answered 403 or 429. Windguru's
     * firewall blocks an IP for "unusual traffic", and a blocked instance that kept going - a sweep
     * of ~1600 requests every three hours, a retry pass after each, 80 more per spot page opened -
     * is exactly the traffic that keeps it blocked.
     */
    static final Duration REFUSAL_PAUSE = Duration.ofMinutes(30);

    /**
     * The wave export does not depend on the forecast model, yet opening a spot page fetches every
     * model at once and each of them used to fetch the waves again: 40 identical requests per page.
     * One fetch is shared by every model asking within this window.
     */
    private static final Duration WAVE_CACHE_TTL = Duration.ofMinutes(10);

    private final OkHttpClient httpClient;
    private final WeatherForecastMapper mapper;
    private final String baseUrl;
    private final Clock clock;
    private final Map<Integer, CachedWaves> waveCache = new ConcurrentHashMap<>();
    private final AtomicLong pausedUntilMillis = new AtomicLong();

    @Autowired
    public ForecastService(WeatherForecastMapper mapper, OkHttpClient httpClient) {
        this(mapper, httpClient, URL, Clock.systemUTC());
    }

    ForecastService(WeatherForecastMapper mapper, OkHttpClient httpClient, String baseUrl, Clock clock) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.clock = clock;
    }

    /**
     * Whether Windguru refused a request recently and every request is held back until the pause
     * runs out. Callers about to send a whole batch check it first rather than failing it spot by spot.
     */
    public boolean isPaused() {
        return clock.millis() < pausedUntilMillis.get();
    }

    /**
     * Thrown instead of sending anything while Windguru's refusal pause lasts, and for the refusal itself.
     */
    public static final class WindguruRefusedException extends IOException {
        public WindguruRefusedException(String message) {
            super(message);
        }
    }

    private record CachedWaves(Mono<Map<String, WaveData>> waves, long expiresAtMillis) {}

    public Mono<ForecastData> getForecastData(int wgSpotId) {
        return getForecastData(wgSpotId, ForecastModel.GFS);
    }

    public Mono<ForecastData> getForecastData(int wgSpotId, ForecastModel forecastModel) {
        final HttpUrl httpUrl = HttpUrl.parse(baseUrl);
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
                // A refusal fails the request for good: subscribing to forecastMono again would be
                // a retry of exactly the request Windguru just turned down.
                .onErrorResume(e -> !(e instanceof WindguruRefusedException), _ -> forecastMono.map(forecasts -> new ForecastData(
                        mapper.toWeatherForecasts(forecasts),
                        Map.of(forecastModel, mapper.toHourlyForecasts(forecasts))
                )));
    }

    public Mono<List<Forecast>> getForecast(int wgSpotId) {
        return getForecastData(wgSpotId).map(ForecastData::daily);
    }

    private record WaveData(Double height, Double period, Integer directionDeg) {}

    private Mono<Map<String, WaveData>> fetchWaveData(int wgSpotId) {
        final long now = clock.millis();
        // Expired entries are never read again, so without this the sweep would leave one behind
        // for every spot until the next pass came round.
        waveCache.values().removeIf(entry -> entry.expiresAtMillis() <= now);
        return waveCache.compute(wgSpotId, (_, cached) -> cached != null && cached.expiresAtMillis() > now
                ? cached
                : new CachedWaves(requestWaveData(wgSpotId).cache(), now + WAVE_CACHE_TTL.toMillis())
        ).waves();
    }

    private Mono<Map<String, WaveData>> requestWaveData(int wgSpotId) {
        final HttpUrl httpUrl = HttpUrl.parse(baseUrl);
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

    private Mono<String> executeHttpRequest(final Request unidentifiedRequest) {
        final Request request = unidentifiedRequest.newBuilder().header("User-Agent", USER_AGENT).build();
        return Mono.<String>create(sink -> {
            if (isPaused()) {
                sink.error(new WindguruRefusedException("Windguru requests paused until "
                        + Instant.ofEpochMilli(pausedUntilMillis.get()) + " after a refusal"));
                return;
            }
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
                        if (response.code() == 403 || response.code() == 429) {
                            pauseAfterRefusal(response.code());
                            sink.error(new WindguruRefusedException("HTTP " + response.code() + ": " + response.message()));
                            return;
                        }
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

    private void pauseAfterRefusal(int statusCode) {
        final long now = clock.millis();
        final long until = now + REFUSAL_PAUSE.toMillis();
        final long previous = pausedUntilMillis.getAndUpdate(current -> Math.max(current, until));
        // Requests already in flight all come back refused at once; one line is enough to say so.
        if (previous <= now) {
            log.error("Windguru refused a request with HTTP {}; pausing every Windguru request until {}. "
                    + "A 403 usually means the server's IP was blocked - see https://micro.windguru.cz",
                    statusCode, Instant.ofEpochMilli(until));
        }
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
